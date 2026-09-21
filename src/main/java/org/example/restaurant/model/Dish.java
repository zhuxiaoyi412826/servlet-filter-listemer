package org.example.restaurant.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 菜品实体（与 t_dish 表一一对应）。 */
public class Dish implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long id;
    public String name;
    public String category;
    public double price;
    public int stock;
    public String imageUrl;
    public String description;
    /** 1 上架，0 下架 */
    public int status;
    public LocalDateTime createdAt;
}
