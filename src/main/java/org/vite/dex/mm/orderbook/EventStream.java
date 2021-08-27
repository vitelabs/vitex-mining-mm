package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EventStream from the trade contract of the chain
 */
public class EventStream {
    private List<OrderEvent> events = Lists.newArrayList();

    public EventStream() {
    }

    public EventStream(List<OrderEvent> events) {
        this.events.addAll(events);
    }

    public List<OrderEvent> getEvents() {
        return events;
    }

    public void addEvent(OrderEvent event) {
        events.add(event);
    }

    /**
     * find the orderEvents whose emitTime is on the left hand of order book
     * @param orderBook
     */
    public void filter(OrderBook orderBook) {
        Map<String, OrderModel> orders = new HashMap<>();
        orderBook.getBuys().forEach(order -> {
            orders.put(order.getOrderId(), order);
        });
        orderBook.getSells().forEach(order -> {
            orders.put(order.getOrderId(), order);
        });

        OrderEvent left = indexOfLeftPoint(orders);
        Optional<OrderEvent> right = indexOfRightPoint(orders, left);
        if (!right.isPresent()) {
            return;
        }
        this.events = this.events.stream().filter(e -> e.getTimestamp() < right.get().getTimestamp())
                .collect(Collectors.toList());
    }

    private OrderEvent indexOfLeftPoint(Map<String, OrderModel> orders) {
        return events.stream().filter(e -> e.getType() == EventType.NewOrder && orders.containsKey(e.getOrderId()))
                .max(Comparator.comparing(OrderEvent::getTimestamp)).get();
    }

    private Optional<OrderEvent> indexOfRightPoint(Map<String, OrderModel> orders, OrderEvent left) {
        Set<String> unFinisheds = new HashSet<>();
        events.forEach(e -> {
            if (e.getType() == EventType.NewOrder && !e.getOrderLog().finished()) {
                unFinisheds.add(e.getOrderId());
            }
            if (e.getType() == EventType.UpdateOrder && e.getOrderLog().finished()) {
                unFinisheds.remove(e.getOrderId());
            }
        });

        return events.stream().filter(e -> e.getType() == EventType.NewOrder && unFinisheds.contains(e.getOrderId())
                && !orders.containsKey(e.getOrderId())).max(Comparator.comparing(OrderEvent::getTimestamp));
    }
}