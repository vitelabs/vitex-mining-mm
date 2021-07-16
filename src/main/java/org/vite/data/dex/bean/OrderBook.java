package org.vite.data.dex.bean;

import com.sun.tools.corba.se.idl.constExpr.Or;

public class OrderBook {
    // back-trace according to a event
    public OrderBook revert(Event e) {
        return new OrderBook();
    }

    // back-trace according to a event
    public OrderBook doEvent(Event e) {
        return new OrderBook();
    }
}
