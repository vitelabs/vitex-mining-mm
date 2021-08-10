package org.vite.dex.mm.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderModel {
    @JSONField(name = "Id")
    private String orderId;

    private BigDecimal amount;

    private BigDecimal quantity;

    private BigDecimal price;

    private String address;

    private boolean side;

    private String tradePair;

    // order created time
    private long timestamp;

    public static OrderModel fromOrderLog(OrderLog orderLog) {
        OrderModel orderModel = new OrderModel();
        orderModel.orderId = orderLog.getOrderId();
        orderModel.amount = orderLog.getChangeAmount();
        orderModel.quantity = orderLog.getChangeQuantity();
        orderModel.price = orderLog.getPrice();
        orderModel.address = orderLog.getAddress();
        orderModel.side = orderLog.isSide();
        orderModel.tradePair = orderLog.getTradePair();
        orderModel.timestamp = orderLog.getOrderCreateTime();
        return orderModel;
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
