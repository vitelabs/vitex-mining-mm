package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.enums.EventType.NewOrder;

/**
 * EventStream from the trade contract of the chain
 */
public class EventStream {
    private List<OrderEvent> events = Lists.newArrayList();

    public List<OrderEvent> getEvents() {
        return events;
    }

    public void addEvent(OrderEvent event) {
        events.add(event);
    }

    public void filter(OrderBook orderBook) {
        Map<String, OrderModel> orders = new HashMap<>();
        orderBook.getBuys().forEach(order -> {
            orders.put(order.getOrderId(), order);
        });
        orderBook.getSells().forEach(order -> {
            orders.put(order.getOrderId(), order);
        });

        // find the orderEvent which emit-time is on the right hand of order book
        OrderEvent left = indexOfLeftPoint(orders);
        OrderEvent right = indexOfRightPoint(orders, left);
        this.events = this.events.stream().filter(e -> e.getTimestamp() < right.getTimestamp())
                .collect(Collectors.toList());
    }

    private OrderEvent indexOfLeftPoint(Map<String, OrderModel> orders) {
        return events.stream().filter(e -> e.getType() == NewOrder && orders.containsKey(e.getOrderId()))
                .max(Comparator.comparing(OrderEvent::getTimestamp)).get();
    }

    private OrderEvent indexOfRightPoint(Map<String, OrderModel> orders, OrderEvent left) {
        return events.stream()
                .filter(e -> e.getType() == NewOrder && !orders.containsKey(e.getOrderId())
                        && e.getTimestamp() >= left.getTimestamp())
                .min(Comparator.comparing(OrderEvent::getTimestamp)).get();
    }
}