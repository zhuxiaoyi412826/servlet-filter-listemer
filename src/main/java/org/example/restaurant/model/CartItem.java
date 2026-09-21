package org.example.restaurant.model;

import java.io.Serializable;

/** 购物车条目（购物车数据存放于 HttpSession 中）。 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long dishId;
    public String name;
    public String imageUrl;
    public double price;
    public int quantity;
    public int stock;
    /** 小计金额（随 JSON 回传到前端） */
    public double amount;

    public double getAmount() {
        return amount;
    }
}
