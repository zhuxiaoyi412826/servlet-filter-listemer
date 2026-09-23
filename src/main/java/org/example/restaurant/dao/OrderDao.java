package org.example.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurant.model.Dish;
import org.example.restaurant.model.Order;
import org.example.restaurant.model.OrderItem;
import org.example.restaurant.util.DbUtil;

/** 订单数据访问，下单过程使用 JDBC 事务保证库存一致性。 */
public final class OrderDao {

    private OrderDao() {
    }

    /**
     * 下单（事务）：校验菜品 -> 扣减库存 -> 写入订单与明细。
     *
     * @param cartItems Map&lt;菜品ID, 数量&gt;
     * @return 完整订单（含明细）
     */
    public static Order create(long userId, Map<Long, Integer> cartItems, String payType, String remark)
            throws SQLException, IllegalArgumentException {
//        静态 create 方法，接收用户 ID、购物车 Map、支付类型、
//        备注；声明抛出数据库异常和非法参数异常，功能是创建订单（需要事务：新增订单 + 订单明细 + 扣库存）。
//        事务边界在本方法内闭环：异常回滚，成功提交。

        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                Order order = insertInTx(c, userId, cartItems, payType, remark);
                c.commit();
                return order;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * 事务版下单：只写数据，**不提交也不回滚**，事务边界交给调用方。
     * PayService 用它在同一个事务内完成「创建订单 + 扣减余额」，保证订单与扣款同生共死。
     *
     * @param c 调用方已开启的事务连接（autoCommit = false）
     */
    public static Order insertInTx(Connection c, long userId, Map<Long, Integer> cartItems,
                                   String payType, String remark)
            throws SQLException, IllegalArgumentException {
//        空购物车直接抛业务异常，让上层捕获后返回给前端，避免空事务白跑。
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("购物车是空的，请先点菜");
        }

//        一次查出购物车所有菜品（减少 N+1 查询），然后逐项做 4 种校验，同时累加总价。
//        注意：这里抛的异常由上层事务回滚，不会真扣库存。
//        使用调用方的事务连接`c`，**批量查询购物车内所有菜品，并且对这些菜品记录加悲观锁**；查询结果存入 Map，key 为菜品 ID，value 是 Dish 实体。
                Map<Long, Dish> dishes = loadForUpdate(c, new ArrayList<>(cartItems.keySet()));
                double total = 0;

//                `cartItems.entrySet()`拿到 Map 全部键值对，增强 for 循环取出每一个`Map.Entry`对象；
//                entry.getKey () 取菜品 ID，entry.getValue () 取购买数量，用于循环校验库存、扣减库存。
                for (Map.Entry<Long, Integer> entry : cartItems.entrySet()) {
                    Dish d = dishes.get(entry.getKey());
                    if (d == null) throw new IllegalArgumentException("菜品已下架或不存在，请刷新购物车");
                    int quantity = entry.getValue();
                    if (quantity <= 0) throw new IllegalArgumentException("菜品【" + d.name + "】数量不合法");
                    if (d.stock < quantity) {
                        throw new IllegalArgumentException("菜品【" + d.name + "】库存不足，仅剩 " + d.stock + " 份");
                    }
                    if (d.status == 0) throw new IllegalArgumentException("菜品【" + d.name + "】已下架");
                    total += d.price * quantity;
                }

//                组装 Order 对象，设置订单号、用户 ID、总金额、支付类型、备注、状态、创建时间
                LocalDateTime now = LocalDateTime.now();
                Order order = new Order();
                order.orderNo = generateOrderNo(now);
                order.userId = userId;
                order.totalAmount = Math.round(total * 100.0) / 100.0;
                order.payType = payType == null || payType.isBlank() ? "CASH" : payType.toUpperCase();
                order.remark = remark;
                order.status = "CREATED";
                order.createdAt = now;
//             向**订单主表 `t_order`**插入一条新订单记录 ？？？ 占位符
                String orderSql = "INSERT INTO t_order(order_no,user_id,total_amount,pay_type,remark,status,created_at)"
                        + " VALUES(?,?,?,?,?,?,NOW())";

//              告诉 JDBC：**插入完成后，返回数据库自动生成的主键（自增 id）**
                /**
                 * 序号	代码	对应字段
                 * 1	ps.setString(1, order.orderNo)	order_no 订单业务编号
                 * 2	ps.setLong(2, userId)	user_id 用户 id
                 * 3	ps.setBigDecimal(3, ...)	totalAmount 订单总金额（金额推荐 BigDecimal，避免浮点精度丢失）
                 * 4	ps.setString(4, order.payType)	pay_type 支付方式
                 * 5	ps.setString(5, order.remark)	remark 备注
                 * 6	ps.setString(6, order.status)	status 订单状态
                 */
                try (PreparedStatement ps = c.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, order.orderNo);
                    ps.setLong(2, userId);
                    ps.setBigDecimal(3, java.math.BigDecimal.valueOf(order.totalAmount));
                    ps.setString(4, order.payType);
                    ps.setString(5, order.remark);
                    ps.setString(6, order.status);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) order.id = keys.getLong(1);
                    }
                }
//             写 t_order_item（订单明细）
                String itemSql = "INSERT INTO t_order_item(order_id,dish_id,dish_name,price,quantity,amount)"
                        + " VALUES(?,?,?,?,?,?)";
//                扣库存，乐观锁条件 防止超卖 最小库存底线（必须大于等于购买数量才能扣）
                String stockSql = "UPDATE t_dish SET stock = stock - ? WHERE id = ? AND stock >= ?";
                for (Map.Entry<Long, Integer> entry : cartItems.entrySet()) {
                    Dish d = dishes.get(entry.getKey());
                    int quantity = entry.getValue();
                    double amount = Math.round(d.price * quantity * 100.0) / 100.0;
                    OrderItem item = new OrderItem();
                    item.orderId = order.id;
                    item.dishId = d.id;
                    item.dishName = d.name;
                    item.imageUrl = d.imageUrl;
                    item.price = d.price;
                    item.quantity = quantity;
                    item.amount = amount;
                    order.items.add(item);

                    try (PreparedStatement ps = c.prepareStatement(itemSql)) {
                        ps.setLong(1, order.id);
                        ps.setLong(2, d.id);
                        ps.setString(3, d.name);
                        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(d.price));
                        ps.setInt(5, quantity);
                        ps.setBigDecimal(6, java.math.BigDecimal.valueOf(amount));
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement(stockSql)) {
                        ps.setInt(1, quantity);
                        ps.setLong(2, d.id);
                        ps.setInt(3, quantity);
                        int rows = ps.executeUpdate();
                        if (rows != 1) throw new IllegalArgumentException("菜品【" + d.name + "】库存不足，请刷新重试");
                    }
                }
//  写库完成，提交/回滚由外层事务决定
                return order;
    }

//    在事务中加锁读取购物车里所有菜品的最新数据（价格、库存、状态），防止并发下单时超卖。  定义悲观锁
    private static Map<Long, Dish> loadForUpdate(Connection c, List<Long> ids) throws SQLException {
        StringBuilder in = new StringBuilder();
//        SQL拼接，生成 IN (?) 的占位符列表，比如 3 个占位符就是 (?,?,?)
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(',');
            in.append('?');
        }
        String sql = "SELECT id,name,category,price,stock,image_url,description,status,created_at"
                + " FROM t_dish WHERE id IN (" + in + ")";

        Map<Long, Dish> map = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Dish d = new Dish();
                    d.id = rs.getLong("id");
                    d.name = rs.getString("name");
                    d.category = rs.getString("category");
                    d.price = rs.getBigDecimal("price").doubleValue();
                    d.stock = rs.getInt("stock");
                    d.imageUrl = rs.getString("image_url");
                    d.description = rs.getString("description");
                    d.status = rs.getInt("status");
                    map.put(d.id, d);
                }
            }
        }
        return map;
    }

// 这段 generateOrderNo 是生成订单编号的工具方法：
    private static String generateOrderNo(LocalDateTime now) {
        String prefix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = (int) (Math.random() * 9000) + 1000;
        return "RS" + prefix + rnd;
    }

//    将 ResultSet 的内容映射到 Order 对象。
    private static Order map(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.id = rs.getLong("id");
        o.orderNo = rs.getString("order_no");
        o.userId = rs.getLong("user_id");
        o.totalAmount = rs.getBigDecimal("total_amount").doubleValue();
        o.payType = rs.getString("pay_type");
        o.remark = rs.getString("remark");
        o.status = rs.getString("status");
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        o.createdAt = ts == null ? null : ts.toLocalDateTime();
        return o;
    }

    /** 我的订单（含明细）。 */
    public static List<Order> findByUser(long userId) throws SQLException {
        String sql = "SELECT id,order_no,user_id,total_amount,pay_type,remark,status,created_at"
                + " FROM t_order WHERE user_id = ? ORDER BY id DESC";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Order> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                for (Order o : list) o.items = findItems(o.id);
                return list;
            }
        }
    }

// 查询订单记录
    public static Order findByNo(String orderNo, long userId) throws SQLException {
        String sql = "SELECT id,order_no,user_id,total_amount,pay_type,remark,status,created_at"
                + " FROM t_order WHERE order_no = ? AND user_id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Order o = map(rs);
                o.items = findItems(o.id);
                return o;
            }
        }
    }
// 查询当前订单的明细
    public static List<OrderItem> findItems(long orderId) throws SQLException {
        String sql = "SELECT i.id,i.order_id,i.dish_id,i.dish_name,i.price,i.quantity,i.amount,d.image_url"
                + " FROM t_order_item i LEFT JOIN t_dish d ON d.id = i.dish_id"
                + " WHERE i.order_id = ? ORDER BY i.id";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderItem> list = new ArrayList<>();
                while (rs.next()) {
                    OrderItem it = new OrderItem();
                    it.id = rs.getLong("id");
                    it.orderId = rs.getLong("order_id");
                    it.dishId = rs.getLong("dish_id");
                    it.dishName = rs.getString("dish_name");
                    it.price = rs.getBigDecimal("price").doubleValue();
                    it.quantity = rs.getInt("quantity");
                    it.amount = rs.getBigDecimal("amount").doubleValue();
                    it.imageUrl = rs.getString("image_url");
                    list.add(it);
                }
                return list;
            }
        }
    }

    /**
     * 事务内锁定并读取订单关键信息（悲观锁），供退款前取金额使用。
     *
     * @return {orderNo,totalAmount,status}，订单不存在或非本人时返回 null
     */
    public static Map<String, Object> lockOrder(Connection c, long userId, long orderId) throws SQLException {
        String sql = "SELECT order_no,total_amount,status FROM t_order WHERE id = ? AND user_id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", rs.getString("order_no"));
                m.put("totalAmount", rs.getBigDecimal("total_amount").doubleValue());
                m.put("status", rs.getString("status"));
                return m;
            }
        }
    }

    /** 取消订单并回滚库存（事务）。 */
    public static boolean cancel(long userId, long orderId) throws SQLException {
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                boolean ok = cancelInTx(c, userId, orderId);
                if (ok) c.commit(); else c.rollback();
                return ok;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * 事务版取消：归还库存并把状态改为 CANCELLED，**不提交不回滚**（由调用方控制）。
     * PayService 用它配合余额退款，保证库存、订单状态、退款三者同事务。
     *
     * @return true 表示成功取消
     */
    public static boolean cancelInTx(Connection c, long userId, long orderId) throws SQLException {
        String query = "SELECT order_no,status FROM t_order WHERE id=? AND user_id=?";
        boolean canCancel;
                try (PreparedStatement ps = c.prepareStatement(query)) {
                    ps.setLong(1, orderId);
                    ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        canCancel = rs.next() && "CREATED".equals(rs.getString("status"));
                    }
                }
                if (!canCancel) {
                    return false;      // 不可取消，由调用方决定回滚还是提交
                }
                // 归还库存
                List<OrderItem> items = findItemsInTx(c, orderId);
                String back = "UPDATE t_dish SET stock = stock + ? WHERE id = ?";
                for (OrderItem it : items) {
                    try (PreparedStatement ps = c.prepareStatement(back)) {
                        ps.setInt(1, it.quantity);
                        ps.setLong(2, it.dishId);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE t_order SET status='CANCELLED' WHERE id=?")) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }
                return true;
    }

    // ==================== 后台：订单管理 ====================

    private static String adminWhere(String orderNo, String status, String from, String to,
                                     String keyword, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1=1 ");
        if (orderNo != null && !orderNo.isBlank()) {
            w.append(" AND o.order_no LIKE ?");
            args.add("%" + orderNo.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            w.append(" AND o.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (from != null && !from.isBlank()) {
            w.append(" AND o.created_at >= ?");
            args.add(from.trim());
        }
        if (to != null && !to.isBlank()) {
            // 含当天：结束日期 + 1 天作为开区间上界
            w.append(" AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)");
            args.add(to.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            w.append(" AND (o.order_no LIKE ? OR u.username LIKE ? OR u.nickname LIKE ? OR u.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return w.toString();
    }

    /** 后台订单列表（带分页，自动附带明细）。 */
    public static List<Map<String, Object>> searchForAdmin(String orderNo, String status, String from, String to,
                                                           String keyword, int limit, int offset) throws SQLException {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(orderNo, status, from, to, keyword, args);
        String sql = "SELECT o.id,o.order_no,o.user_id,o.total_amount,o.pay_type,o.remark,o.status,o.created_at,"
                + " u.username,u.nickname,u.phone,u.avatar"
                + " FROM t_order o LEFT JOIN t_user u ON u.id = o.user_id" + where
                + " ORDER BY o.id DESC LIMIT ? OFFSET ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object a : args) ps.setObject(i++, a);
            ps.setInt(i++, Math.max(limit, 1));
            ps.setInt(i, Math.max(offset, 0));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("orderNo", rs.getString("order_no"));
                    m.put("userId", rs.getLong("user_id"));
                    m.put("totalAmount", rs.getBigDecimal("total_amount").doubleValue());
                    m.put("payType", rs.getString("pay_type"));
                    m.put("remark", rs.getString("remark"));
                    m.put("status", rs.getString("status"));
                    java.sql.Timestamp ts = rs.getTimestamp("created_at");
                    m.put("createdAt", ts == null ? null : ts.toLocalDateTime());
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("username", rs.getString("username"));
                    u.put("nickname", rs.getString("nickname"));
                    u.put("phone", rs.getString("phone"));
                    u.put("avatar", rs.getString("avatar"));
                    m.put("user", u);
                    m.put("items", findItems(rs.getLong("id")));
                    list.add(m);
                }
                return list;
            }
        }
    }

    /** 后台订单总数（条件同 searchForAdmin）。 */
    public static int countForAdmin(String orderNo, String status, String from, String to,
                                    String keyword) throws SQLException {
        List<Object> args = new ArrayList<>();
        String where = adminWhere(orderNo, status, from, to, keyword, args);
        String sql = "SELECT COUNT(1) FROM t_order o LEFT JOIN t_user u ON u.id = o.user_id" + where;
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 后台订单状态分布（用于订单管理页顶部概览）。 */
    public static Map<String, Object> statusOverview() throws SQLException {
        String sql = "SELECT"
                + " COUNT(1) AS total,"
                + " SUM(CASE WHEN status <> 'CANCELLED' THEN 1 ELSE 0 END) AS valid,"
                + " SUM(CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END) AS created,"
                + " SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) AS paid,"
                + " SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,"
                + " SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled,"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount ELSE 0 END),0) AS revenue"
                + " FROM t_order";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("total", rs.getLong("total"));
            m.put("valid", rs.getLong("valid"));
            m.put("created", rs.getLong("created"));
            m.put("paid", rs.getLong("paid"));
            m.put("completed", rs.getLong("completed"));
            m.put("cancelled", rs.getLong("cancelled"));
            m.put("revenue", rs.getBigDecimal("revenue").doubleValue());
            return m;
        }
    }

    /**
     * 后台改单：状态流转并在必要时回滚/重新占用库存（事务）。
     *
     * @throws IllegalArgumentException 非法流转或库存不足
     */
    public static void updateStatusAsAdmin(long orderId, String target) throws SQLException {
        String status = "COMPLETED".equalsIgnoreCase(target) ? "COMPLETED"
                : "PAID".equalsIgnoreCase(target) ? "PAID"
                : "CANCELLED".equalsIgnoreCase(target) ? "CANCELLED"
                : "CREATED";
        try (Connection c = DbUtil.open()) {
            c.setAutoCommit(false);
            try {
                String cur;
                try (PreparedStatement ps = c.prepareStatement("SELECT status FROM t_order WHERE id = ?")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("订单不存在");
                        cur = rs.getString("status");
                    }
                }
                if (cur.equals(status)) throw new IllegalArgumentException("订单已处于该状态");
                boolean cancelledNow = "CANCELLED".equals(status);
                boolean cancelledBefore = "CANCELLED".equals(cur);
                if (cancelledNow && !cancelledBefore) {
                    // 取消：归还库存
                    for (OrderItem it : findItemsInTx(c, orderId)) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE t_dish SET stock = stock + ? WHERE id = ?")) {
                            ps.setInt(1, it.quantity);
                            ps.setLong(2, it.dishId);
                            ps.executeUpdate();
                        }
                    }
                } else if (!cancelledNow && cancelledBefore) {
                    // 恢复：重新占用库存
                    for (OrderItem it : findItemsInTx(c, orderId)) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE t_dish SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
                            ps.setInt(1, it.quantity);
                            ps.setLong(2, it.dishId);
                            ps.setInt(3, it.quantity);
                            if (ps.executeUpdate() != 1) {
                                throw new IllegalArgumentException("库存不足，无法恢复订单，请先补货");
                            }
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE t_order SET status = ? WHERE id = ?")) {
                    ps.setString(1, status);
                    ps.setLong(2, orderId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ==================== 后台：收入统计 ====================

    /** 收入概览：总收入、今日、本月、客单价等。 */
    public static Map<String, Object> incomeOverview() throws SQLException {
        String sql = "SELECT"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount ELSE 0 END),0) AS revenue,"
                + " IFNULL(SUM(CASE WHEN status = 'CANCELLED' THEN total_amount ELSE 0 END),0) AS cancelled_amount,"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' AND DATE(created_at) = CURDATE()"
                + "   THEN total_amount ELSE 0 END),0) AS today_revenue,"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)"
                + "   THEN total_amount ELSE 0 END),0) AS yesterday_revenue,"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' AND DATE_FORMAT(created_at,'%Y-%m') = DATE_FORMAT(CURDATE(),'%Y-%m')"
                + "   THEN total_amount ELSE 0 END),0) AS month_revenue,"
                + " COUNT(CASE WHEN status <> 'CANCELLED' THEN 1 END) AS valid_orders,"
                + " COUNT(CASE WHEN status <> 'CANCELLED' AND DATE(created_at) = CURDATE() THEN 1 END) AS today_orders"
                + " FROM t_order";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            double revenue = rs.getBigDecimal("revenue").doubleValue();
            long valid = rs.getLong("valid_orders");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("revenue", round2(revenue));
            m.put("cancelledAmount", rs.getBigDecimal("cancelled_amount").doubleValue());
            m.put("todayRevenue", rs.getBigDecimal("today_revenue").doubleValue());
            m.put("yesterdayRevenue", rs.getBigDecimal("yesterday_revenue").doubleValue());
            m.put("monthRevenue", rs.getBigDecimal("month_revenue").doubleValue());
            m.put("validOrders", valid);
            m.put("todayOrders", rs.getLong("today_orders"));
            m.put("avgOrderValue", valid == 0 ? 0D : round2(revenue / valid));
            return m;
        }
    }

    /** 最近 N 天每日收入趋势（含 0 值补位由前端按日期序列处理）。 */
    public static List<Map<String, Object>> dailyIncome(int days) throws SQLException {
        String sql = "SELECT DATE(created_at) AS day,"
                + " COUNT(1) AS orders,"
                + " IFNULL(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount ELSE 0 END),0) AS revenue"
                + " FROM t_order WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)"
                + " GROUP BY DATE(created_at) ORDER BY day";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(days - 1, 0));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("day", rs.getDate("day").toString());
                    m.put("orders", rs.getLong("orders"));
                    m.put("revenue", rs.getBigDecimal("revenue").doubleValue());
                    list.add(m);
                }
                return list;
            }
        }
    }

    /** 菜品销量排行（按份数）。 */
    public static List<Map<String, Object>> dishRanking(int limit) throws SQLException {
        String sql = "SELECT i.dish_name AS name, SUM(i.quantity) AS quantity,"
                + " IFNULL(SUM(i.amount),0) AS revenue, d.category AS category"
                + " FROM t_order_item i JOIN t_order o ON o.id = i.order_id"
                + " LEFT JOIN t_dish d ON d.id = i.dish_id"
                + " WHERE o.status <> 'CANCELLED'"
                + " GROUP BY i.dish_name, d.category ORDER BY quantity DESC, revenue DESC LIMIT ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(limit, 1));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("category", rs.getString("category"));
                    m.put("quantity", rs.getLong("quantity"));
                    m.put("revenue", rs.getBigDecimal("revenue").doubleValue());
                    list.add(m);
                }
                return list;
            }
        }
    }

    /** 支付方式分布。 */
    public static List<Map<String, Object>> payTypeStats() throws SQLException {
        String sql = "SELECT pay_type AS payType, COUNT(1) AS orders,"
                + " IFNULL(SUM(total_amount),0) AS revenue"
                + " FROM t_order WHERE status <> 'CANCELLED' GROUP BY pay_type ORDER BY revenue DESC";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("payType", rs.getString("payType"));
                m.put("orders", rs.getLong("orders"));
                m.put("revenue", rs.getBigDecimal("revenue").doubleValue());
                list.add(m);
            }
            return list;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static List<OrderItem> findItemsInTx(Connection c, long orderId) throws SQLException {
        String sql = "SELECT id,dish_id,quantity FROM t_order_item WHERE order_id=?";
        List<OrderItem> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem it = new OrderItem();
                    it.id = rs.getLong("id");
                    it.dishId = rs.getLong("dish_id");
                    it.quantity = rs.getInt("quantity");
                    list.add(it);
                }
            }
        }
        return list;
    }
}
