package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.AccBlockVmLogs;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.AccountBlock;

import java.util.Comparator;
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
    private ViteCli viteCli;

    public EventStream() {}

    public EventStream(List<OrderEvent> events) {
        this.events.addAll(events);
    }

    public List<OrderEvent> getEvents() {
        events.sort(Comparator.comparing(OrderEvent::getTimestamp));
        return events;
    }

    public void addEvent(OrderEvent event) {
        events.add(event);
    }

    /**
     * find the orderEvents whose emitTime is on the left hand of order book
     * 
     * @param orderBook
     */
    public void filter(OrderBook orderBook) {
        Long currBlockHeight = orderBook.getCurrBlockHeight();
        Map<String, OrderModel> orders = orderBook.getOrders();

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



    public void init(Long startHeight, Long endHeight, Tokens tokens) {
        List<AccBlockVmLogs> vmLogInfoList = viteCli.getAccBlocksByHeightRange(startHeight, endHeight,
                1000);

        // parse vmLogs and group these vmLogs by trade-pair
        EventStream eventStream = new EventStream();
        for (AccBlockVmLogs accBlock : vmLogInfoList) {
            BlockEvent blockEvent = BlockEvent.fromAccBlockVmLogs(accBlock, tokens);

            blockEvent.getOrderEvents().forEach(orderEvent -> {
                if (orderEvent.tradePair().equals(tp.getTradePair()) && !orderEvent.ignore()) {
                    AccountBlock block = accountBlockMap.get(orderEvent.getBlockHash());
                    if (block != null) {
                        orderEvent.setTimestamp(block.getTimestampRaw());
                    }
                    eventStream.addEvent(orderEvent);
                }
            });
        }
    }


}
