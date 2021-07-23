package org.vite.dex.mm.entity;

import org.vite.dex.mm.constant.enums.OrderEventType;

public class OrderEvent {
    private long id;

    private long timestamp;

    private OrderEventType type;

    private int quantity;

    private int price;

    private int side;


}
