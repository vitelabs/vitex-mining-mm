package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.bean.Order;
import org.vite.dex.mm.bean.OrderEvent;

import java.math.BigDecimal;
import java.util.List;



public interface OrderBook {
    // back-trace according to a event
    public void revert(OrderEvent e);

    // back-trace according to a event
    public void deal(OrderEvent e);

    // init
    public void init(List<Order> buys, List<Order> sells);



    class Impl implements OrderBook {

        private long curEventId;

        private List<Order> buys;
        private List<Order> sells;
        private BigDecimal topSellPrice;
        private BigDecimal topBuyPrice;

        // back-trace according to a event
        public void revert(OrderEvent e) {
            // curEventId == e.id + 1
        }

        // back-trace according to a event
        public void deal(OrderEvent e) {
            // curEventId == e.id - 1
        }

        public void init(List<Order> buys, List<Order> sells) {}
    }

}
