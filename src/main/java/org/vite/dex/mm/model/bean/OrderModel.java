package org.vite.dex.mm.model.bean;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.vite.dex.mm.constant.consist.MiningConst;
import org.vite.dex.mm.model.pojo.CurrentOrder;

import java.math.BigDecimal;

@Data
public class OrderModel {
    @JSONField(name = "Id")
    private String orderId;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal amount;

    private String address;

    private boolean side;

    private String tradePair;

    private long timestamp; // order created time

    // private OrderLog log;

    public static OrderModel fromCurrentOrder(CurrentOrder currOrder, String tradeTokenId, String quoteTokenId) {
        OrderModel orderModel = new OrderModel();
        orderModel.setOrderId(currOrder.getOrderId());
        orderModel.setAddress(currOrder.getAddress());
        orderModel.setPrice(currOrder.getPrice());
        orderModel.setSide(currOrder.isSide());
        orderModel.setTimestamp(currOrder.getTimestamp());
        orderModel.setTradePair(tradeTokenId + MiningConst.UnderscoreStr + quoteTokenId);
        orderModel.setQuantity(currOrder.getQuantity().subtract(currOrder.getExecutedQuantity()));
        orderModel.setAmount(currOrder.getAmount().subtract(currOrder.getExecutedAmount()));

        return orderModel;
    }

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
        // orderModel.log = orderLog;
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

    public boolean emptyAddress() {
        return StringUtils.isEmpty(this.address);
    }

    public String hash() {
        return amount.toString() + quantity.toString() + price.toString();
    }
}
