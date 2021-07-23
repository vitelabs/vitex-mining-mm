package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderBook {
    // the buy first price
    BigDecimal buy1Price;

    // the sell first price
    BigDecimal sell1Price;

    // the buy orders of the orderBook
    List<Order> buyOrders;

    // the sell orders of the orderBook
    List<Order> sellOrders;
}
