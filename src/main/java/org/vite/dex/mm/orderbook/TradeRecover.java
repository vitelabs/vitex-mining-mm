package org.vite.dex.mm.orderbook;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getEventType;

/**
 * 1. prepare order book, get the current order book 
 * 2. prepare events, get all events from last cycle to current 
 * 3. recover order book, recover order order to last cycle by events 
 * 4. mm mining, calculate the market making mining rewards
 */
@Slf4j
public class TradeRecover {

    /**
     * recover all orderbooks to start time of last cycle
     * 
     * @param orderBooks
     * @param time
     * @param tokens
     * @param viteCli
     * @return
     * @throws IOException
     */
    public RecoverResult recoverInTime(OrderBooks orderBooks, Long time, Tokens tokens, ViteCli viteCli)
            throws IOException {
        Long startHeight = viteCli.getContractChainHeight(time) + 1;
        Long endHeight = orderBooks.getCurrentHeight() - 1;
        BlockEventStream stream = new BlockEventStream(startHeight, endHeight);
        stream.init(viteCli, tokens);

        return recoverInTime(orderBooks, stream, tokens, viteCli);
    }

    public RecoverResult recoverInTime(OrderBooks orderBooks, BlockEventStream stream, Tokens tokens, ViteCli viteCli)
            throws IOException {
        stream.travel(orderBooks, true, true);
        RecoverResult result = RecoverResult.builder().orderBooks(orderBooks).stream(stream).build();

        return result;
    }

    /**
     * divide and conquer: group orders by timeUnit and filled with address
     * respectively
     * 
     * @param orders
     * @throws Exception
     */
    public void fillAddressForOrdersGroupByTimeUnit(Map<String, OrderBook> books, ViteCli viteCli) throws Exception {
        List<OrderModel> allOrders = new ArrayList<>();
        books.values().forEach(orderBook -> {
            allOrders.addAll(orderBook.getOrders().values());
        });

        List<OrderModel> orders = allOrders;

        orders = orders.stream().filter(t -> t.emptyAddress()).collect(Collectors.toList());

        Map<Long, List<OrderModel>> orderGroups = orders.stream()
                .collect(Collectors.groupingBy(t -> t.getTimestamp() / TimeUnit.MINUTES.toSeconds(10)));

        for (List<OrderModel> v : orderGroups.values()) {
            fillAddressForOrders(v, viteCli);
        }

        orders.stream().forEach(order -> {
            assert !order.emptyAddress();
        });

    }

    /**
     * Add missing address to the restored orderBook
     *
     * @param orders
     * @throws IOException
     */
    public void fillAddressForOrders(Collection<OrderModel> orders, ViteCli viteCli) throws Exception {
        Map<String, OrderModel> orderMap = orders.stream()
                .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (k0, k1) -> k0));

        long start = orders.stream().min(Comparator.comparing(OrderModel::getTimestamp)).get().getTimestamp();
        long end = orders.stream().max(Comparator.comparing(OrderModel::getTimestamp)).get().getTimestamp();

        start = start - TimeUnit.MINUTES.toSeconds(5);
        end = end + TimeUnit.MINUTES.toSeconds(5);

        orderMap = fillAddressForOrders(orderMap, start, end, viteCli);
        if (orderMap.isEmpty()) {
            return;
        }

        // downward and upward
        int cnt = 1;
        while (true) {
            long start0 = start - TimeUnit.MINUTES.toSeconds(5);
            orderMap = fillAddressForOrders(orderMap, start0, start, viteCli);
            if (orderMap.isEmpty()) {
                break;
            }

            long end1 = end + TimeUnit.MINUTES.toSeconds(5);
            orderMap = fillAddressForOrders(orderMap, end, end1, viteCli);
            if (orderMap.isEmpty()) {
                break;
            }

            start = start0;
            end = end1;

            if (++cnt >= 100) {
                log.error("address of Order is not found");
                throw new RuntimeException("the address of Order is not found!");
            }
        }
    }

    /**
     * 1.get height range of contract-chain between startTime to endTime
     * 2.get eventLogs in the range of height 
     * 3.find NewOrder eventLog, filled the address in Order
     *
     * @param orderMap
     * @param startTime
     * @param endTime
     * @return
     * @throws IOException
     */
    private Map<String, OrderModel> fillAddressForOrders(Map<String, OrderModel> orderMap, long startTime, long endTime,
            ViteCli viteCli) throws IOException {
        Long startHeight = viteCli.getContractChainHeight(startTime);
        Long endHeight = viteCli.getContractChainHeight(endTime);

        List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight, 1000);

        for (VmLogInfo vmLogInfo : vmLogInfoList) {
            Vmlog vmlog = vmLogInfo.getVmlog();
            byte[] event = vmlog.getData();
            EventType eventType = getEventType(vmlog.getTopicsRaw());
            if (eventType == NewOrder) {
                DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                String newOrderId = Hex.toHexString(dexOrder.getOrder().getId().toByteArray());
                OrderModel order = orderMap.get(newOrderId);
                if (order != null && StringUtils.isEmpty(order.getAddress())) {
                    order.setAddress(
                            ViteDataDecodeUtils.getShowAddress(dexOrder.getOrder().getAddress().toByteArray()));
                }
            }
        }
        orderMap = orderMap.values().stream().filter(t -> t.emptyAddress())
                .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (k0, k1) -> k0));
        return orderMap;
    }

    @Data
    @Builder
    public static class RecoverResult {
        private OrderBooks orderBooks;
        private BlockEventStream stream;
    }
}
