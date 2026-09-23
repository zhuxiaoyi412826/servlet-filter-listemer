package org.example.restaurant.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 余额流水实体（与 t_balance_log 表一一对应）。 */
public class BalanceLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long id;
    public Long userId;
    /** 变动金额：负数为支出，正数为收入 */
    public double changeAmount;
    /** 变动之后的余额快照（流水的核心：花了多少、还剩多少） */
    public double balanceAfter;
    /** INIT 初始赠金 / SPEND 消费 / REFUND 退款 / RECHARGE 充值 */
    public String type;
    public String remark;
    public LocalDateTime createdAt;
}
