package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.bean.OrderEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * the corresponding order book to each trade-pair
 */
public class EventStream {
    private List<OrderEvent> events;

    // 1.support getting events of specific time-interval
    public List<OrderEvent> getEventsByTimeRange(long start, long end) {
        return new ArrayList<>();
    }

    public List<OrderEvent> getEventsByIdRange(long start, long end) {
        return new ArrayList<>();
    }
}
