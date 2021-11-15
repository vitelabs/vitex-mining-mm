package org.vite.dex.mm.orderbook.ifaces;

import org.vite.dex.mm.model.bean.OrderEvent;
import org.vite.dex.mm.orderbook.OrderBook;

public interface IOrderEventHandleAware {
    default void beforeRevert(OrderBook book, OrderEvent event) {
    }

    default void afterRevert(OrderBook book, OrderEvent event) {
    }

    default void beforeOnward(OrderBook book, OrderEvent event) {
    }

    default void aferOnward(OrderBook book, OrderEvent event) {
    }

    default void revert(Runnable run, OrderBook book, OrderEvent event) {
        beforeRevert(book, event);
        run.run();
        afterRevert(book, event);
    }

    default void onward(Runnable run, OrderBook book, OrderEvent event) {
        beforeOnward(book, event);
        run.run();
        aferOnward(book, event);
    }
}
