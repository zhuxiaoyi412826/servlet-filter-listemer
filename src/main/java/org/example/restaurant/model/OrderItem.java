package org.example.restaurant.model;

import java.io.Serializable;

/** 订单明细实体（与 t_order_item 表一一对应）。 */
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long id;
    public Long orderId;
    public Long dishId;
    public String dishName;
    public String imageUrl;
    public double price;
    public int quantity;
    public double amount;
}
