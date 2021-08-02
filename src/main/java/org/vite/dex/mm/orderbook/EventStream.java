package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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



    public void filter(OrderBook orderBook) {
        Map<String, OrderModel> orders = new HashMap<>();
        orderBook.getBuys().forEach(order -> {
            orders.put(order.getId(), order);
        });
        orderBook.getSells().forEach(order -> {
            orders.put(order.getId(), order);
        });

        Map<String, List<OrderEvent>> blockHashEventMap =
                events.stream().collect(Collectors.groupingBy(event -> event.getVmLogInfo().getAccountBlockHashRaw()));



        int len = orders.size();


        for (int i = len - 1; i >= 0; i--) {
            OrderEvent event = events.get(i);
            if (event.getType() == OrderEventType.OrderNew) {
                if (!orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                    continue;
                }
            } else if (event.getType() == OrderEventType.OrderUpdate) {
                if (event.getStatus() == OrderUpdateInfoStatus.Cancelled && orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                    continue;
                } else if (event.getStatus() == OrderUpdateInfoStatus.FullyExecuted
                        && orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                    continue;
                } else if (event.getStatus() == OrderUpdateInfoStatus.PartialExecuted) {
                    List<OrderEvent> orderEvents = blockHashEventMap.get(event.getVmLogInfo().getAccountBlockHashRaw());
                    OrderEvent orderEvent = findNewOrder(orderEvents);
                    if (!orders.containsKey(orderEvent.getOrderId())) {
                        event.setDel(true);
                        continue;
                    }
                }
            }
            break;
        }

        this.events = this.events.stream().filter(t -> t.isDel()).collect(Collectors.toList());
    }

    private OrderEvent findNewOrder(List<OrderEvent> orderEvents) {
        return orderEvents.stream().filter(event -> {
            return event.getType() == OrderEventType.OrderNew;
        }).findFirst().get();
    }
}
