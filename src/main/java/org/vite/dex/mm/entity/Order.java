package org.vite.dex.mm.entity;

import lombok.Data;
import org.vite.dex.mm.constant.enums.OrderSide;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.constant.enums.OrderType;

import java.math.BigDecimal;

@Data
public class Order {

    Long createTime;

    //owner address
    String address;

    String orderId;

    String orderHash;

    // the trade pair in which the order located
    TradePair tradePair;

    OrderSide side;

    double price;

    double quantity;

    // price * quantity
    String amount;

    String filledQuantity;

    String filledAmount;

    String filledPercent;

    String filledAvgPrice;

    OrderStatus status;

    OrderType type;

    // the perDay mm-reward for USDT market
    BigDecimal mmRewardPerDay;
}
