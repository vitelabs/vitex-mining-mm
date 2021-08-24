package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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

        OrderEvent left = indexOfLeftPoint(orders);
        OrderEvent right = indexOfRightPoint(orders, left);

        events.forEach(e -> {
            if (e.getTimestamp() >= right.getTimestamp()) {
                e.setDel(true);
            }
        });
        this.events = this.events.stream().filter(t -> !t.isDel()).collect(Collectors.toList());
        System.out.println(events);
    }

    private OrderEvent indexOfLeftPoint(Map<String, OrderModel> orders) {
        return events.stream().filter(new Predicate<OrderEvent>() {
            @Override
            public boolean test(OrderEvent t) {
                // if (t.getTimestamp() < 1570087455L) {
                // return false;
                // }
                if (t.getType() != NewOrder) {
                    return false;
                }
                if (!orders.containsKey(t.getOrderId())) {
                    // System.out.println(
                    // String.format("false-%d-%s-%s-%s-%s", t.getTimestamp(), new
                    // Date(t.getTimestamp() * 1000),
                    // t.getOrderId(), t.getBlockHash(), t.getTradePairSymbol()));
                    return false;
                }
                // System.out.println(String.format("true-%d-%s-%s-%s-%s", t.getTimestamp(),
                // new Date(t.getTimestamp() * 1000), t.getOrderId(), t.getBlockHash(),
                // t.getTradePairSymbol()));
                return true;
            }
        }).max(Comparator.comparing(OrderEvent::getTimestamp)).get();
    }

    private OrderEvent indexOfRightPoint(Map<String, OrderModel> orders, OrderEvent left) {
        return events.stream().filter(new Predicate<OrderEvent>() {
            @Override
            public boolean test(OrderEvent t) {
                if (t.getType() != NewOrder) {
                    return false;
                }
                if (orders.containsKey(t.getOrderId())) {
                    return false;
                }
                if (t.getTimestamp() < left.getTimestamp()) {
                    return false;
                }
                return true;
            }
        }).min(Comparator.comparing(OrderEvent::getTimestamp)).get();
    }
}
