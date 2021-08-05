package org.vite.dex.mm.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderModel {
    @JSONField(name="Id")
    private String orderId;

    private BigDecimal amount;

    private BigDecimal quantity;

    private BigDecimal price;

    private String address;

    private boolean side;

    private String tradePair;

    public static OrderModel fromOrderLog(OrderLog orderLog) {
        OrderModel result = new OrderModel();
        result.orderId = orderLog.getOrderId();
        result.amount = orderLog.getChangeAmount();
        result.quantity = orderLog.getChangeQuantity();
        result.price = orderLog.getPrice();
        result.address = orderLog.getAddress();
        result.side = orderLog.isSide();
        result.tradePair = orderLog.getTradePair();
        return result;
    }

    public void onward(OrderLog orderLog) {
        if (!this.getOrderId().equals(orderLog.getOrderId())) {
            return;
        }
        this.quantity = this.quantity.subtract(orderLog.getChangeQuantity());
        this.amount = this.amount.subtract(orderLog.getChangeAmount());
    }

    public void revert(OrderLog orderLog) {
        if (!this.getOrderId().equals(orderLog.getOrderId())) {
            return;
        }
        this.quantity = this.quantity.add(orderLog.getChangeQuantity());
        this.amount = this.amount.add(orderLog.getChangeAmount());
    }
}
