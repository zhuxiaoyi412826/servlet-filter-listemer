package org.example.restaurant.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 订单实体（与 t_order 表一一对应）。 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long id;
    public String orderNo;
    public Long userId;
    public double totalAmount;
    public String payType;
    public String remark;
    /** CREATED / PAID / CANCELLED */
    public String status;
    public LocalDateTime createdAt;
    /** 明细列表，由 OrderDao 二次查询填充 */
    public List<OrderItem> items = new ArrayList<>();
}
