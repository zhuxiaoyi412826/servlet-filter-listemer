package org.example.restaurant.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体（与 t_user 表一一对应）。
 * transient 字段不会被 Json 序列化输出，避免口令泄露到前端。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long id;
    public String username;
    /** 口令摘要，不回显到前端 */
    public transient String passwordHash;
    /** 盐值，不回显到前端 */
    public transient String salt;
    public String nickname;
    public String phone;
    public String avatar;
    public String address;
    public String role;
    /** 账号状态：1 正常 0 禁用 */
    public Integer status;
    public LocalDateTime createdAt;

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /** 被禁用的账号不能登录 */
    public boolean isDisabled() {
        return status != null && status.intValue() == 0;
    }

    /** 昵称缺失时回退到用户名 */
    public String displayName() {
        return (nickname == null || nickname.isBlank()) ? username : nickname;
    }
}
