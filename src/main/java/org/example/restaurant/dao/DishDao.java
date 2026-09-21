package org.example.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.restaurant.model.Dish;
import org.example.restaurant.util.DbInit;
import org.example.restaurant.util.DbUtil;

/** 菜品数据访问（原生 JDBC，动态拼接条件仍使用预编译占位符防止 SQL 注入）。 */
public final class DishDao {

    private static final String COLUMNS = "id,name,category,price,stock,image_url,description,status,created_at";

    private DishDao() {
    }

    /**
     * 菜品列表检索。
     *
     * @param category   分类，空表示全部
     * @param keyword    名称/描述关键字，支持空格分隔的多关键字
     * @param onlyOnSale 是否仅返回上架商品
     */
    public static List<Dish> search(String category, String keyword, boolean onlyOnSale) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM t_dish WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (onlyOnSale) sql.append(" AND status = 1");
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            for (String k : keyword.trim().split("\\s+")) {
                sql.append(" AND (name LIKE ? OR description LIKE ? OR category LIKE ?)");
                String like = "%" + k + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
        }
        sql.append(" ORDER BY FIELD(category,'热菜','凉菜','主食','汤羹','饮品'), id");
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Dish> list = new ArrayList<>();
                while (rs.next()) list.add(DbInit.mapDish(rs));
                return list;
            }
        }
    }

    public static Dish findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM t_dish WHERE id = ?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? DbInit.mapDish(rs) : null;
            }
        }
    }

    /** 按 id 批量查询并返回有序 Map，便于购物车/下单回显。 */
    public static Map<Long, Dish> findMapByIds(List<Long> ids) throws SQLException {
        Map<Long, Dish> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(',');
            in.append('?');
        }
        String sql = "SELECT " + COLUMNS + " FROM t_dish WHERE id IN (" + in + ")";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Dish d = DbInit.mapDish(rs);
                    map.put(d.id, d);
                }
            }
        }
        return map;
    }

    /** 所有分类（用于前端筛选栏）。 */
    public static List<String> categories() throws SQLException {
        String sql = "SELECT category FROM t_dish WHERE status=1 GROUP BY category ORDER BY category";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getString(1));
            return list;
        }
    }

    /** 管理端：完整列表（含下架）。 */
    public static List<Dish> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM t_dish ORDER BY status DESC, id DESC";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Dish> list = new ArrayList<>();
            while (rs.next()) list.add(DbInit.mapDish(rs));
            return list;
        }
    }

    public static long insert(Dish d) throws SQLException {
        String sql = "INSERT INTO t_dish(name,category,price,stock,image_url,description,status,created_at)"
                + " VALUES(?,?,?,?,?,?,?,NOW())";
        try (Connection c = DbUtil.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.name);
            ps.setString(2, d.category);
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(d.price));
            ps.setInt(4, d.stock);
            ps.setString(5, d.imageUrl);
            ps.setString(6, d.description);
            ps.setInt(7, d.status == 0 ? 0 : 1);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return 0;
        }
    }

    public static int update(Dish d) throws SQLException {
        String sql = "UPDATE t_dish SET name=?, category=?, price=?, stock=?, image_url=?, description=?, status=?"
                + " WHERE id=?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.name);
            ps.setString(2, d.category);
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(d.price));
            ps.setInt(4, d.stock);
            ps.setString(5, d.imageUrl);
            ps.setString(6, d.description);
            ps.setInt(7, d.status == 0 ? 0 : 1);
            ps.setLong(8, d.id);
            return ps.executeUpdate();
        }
    }

    public static int delete(long id) throws SQLException {
        String sql = "DELETE FROM t_dish WHERE id=?";
        try (Connection c = DbUtil.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}
