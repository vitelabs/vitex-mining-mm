package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.entity.OrderEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * EventStream from the trade contract of the chain
 */
public class EventStream {

    private List<OrderEvent> events = new ArrayList<>();

    public List<OrderEvent> getEvents() {
        return events;
    }

    public void addEvent(OrderEvent event) {
        events.add(event);
    }

}
