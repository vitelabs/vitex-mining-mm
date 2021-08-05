package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.constant.enums.EventType.UpdateOrder;

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

        Map<String, List<OrderEvent>> blockHashEventMap =
                events.stream().collect(Collectors.groupingBy(event -> event.getVmLogInfo().getAccountBlockHashRaw()));

        int len = events.size();
        for (int i = len - 1; i >= 0; i--) {
            OrderEvent event = events.get(i);
            if (event.getType() == NewOrder) {
                if (!orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                }
            } else if (event.getType() == UpdateOrder) {
                if (event.getStatus() == OrderUpdateInfoStatus.Cancelled && orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                } else if (event.getStatus() == OrderUpdateInfoStatus.FullyExecuted
                        && orders.containsKey(event.getOrderId())) {
                    event.setDel(true);
                } else if (event.getStatus() == OrderUpdateInfoStatus.PartialExecuted) {
                    List<OrderEvent> orderEvents = blockHashEventMap.get(event.getVmLogInfo().getAccountBlockHashRaw());
                    OrderEvent orderEvent = findNewOrder(orderEvents);
                    if (!orders.containsKey(orderEvent.getOrderId())) {
                        event.setDel(true);
                    }
                }
            }
        }

        this.events = this.events.stream().filter(t -> !t.isDel()).collect(Collectors.toList());
    }

    private OrderEvent findNewOrder(List<OrderEvent> orderEvents) {
        return orderEvents.stream().filter(event -> {
            return event.getType() == NewOrder;
        }).findFirst().get();
    }
}
