package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.entity.OrderEvent;

import java.util.Comparator;
import java.util.List;

/**
 * EventStream from the trade contract of the chain
 */
public class EventStream {
    private List<OrderEvent> events = Lists.newArrayList();

    public EventStream() {}

    public EventStream(List<OrderEvent> events) {
        this.events.addAll(events);
    }

    public List<OrderEvent> getEvents() {
        events.stream().sorted(Comparator.comparing(OrderEvent::getTimestamp));
        return events;
    }

    public void addEvent(OrderEvent event) {
        events.add(event);
    }
}
