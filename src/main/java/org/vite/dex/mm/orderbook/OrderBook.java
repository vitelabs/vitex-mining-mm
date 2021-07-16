package org.vite.dex.mm.orderbook;

import lombok.Getter;
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

    public List<Order> getBuys();

    public List<Order> getSells();



    class Impl implements OrderBook {
        private long curEventId;

        @Getter
        protected List<Order> buys;
        @Getter
        protected List<Order> sells;
        protected BigDecimal topSellPrice;
        protected BigDecimal topBuyPrice;

        // back-trace according to a event
        public void revert(OrderEvent e) {
            // curEventId == e.id + 1
            // buys.0, sells.0
        }

        // back-trace according to a event
        public void deal(OrderEvent e) {
            // curEventId == e.id - 1
            // buys.0, sells.0
        }

        public void init(List<Order> buys, List<Order> sells) {}
    }

}
