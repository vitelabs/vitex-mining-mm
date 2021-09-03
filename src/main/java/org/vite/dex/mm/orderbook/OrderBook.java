package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

public interface OrderBook extends IOrderEventHandler, IBlockEventHandler {
    // init method
    void init(List<OrderModel> orderModels, Long blockHeight);

    List<OrderModel> getBuys();

    List<OrderModel> getSells();

    Map<String, OrderModel> getOrders(); // <OrderId,OrderModel>

    BigDecimal getBuy1Price();

    BigDecimal getSell1Price();

    Long getCurrBlockHeight();

    BigDecimal getAmountSum();

    int getAddCnt();

    int getRemoveCnt();

    String hash();

    @Slf4j
    class Impl implements OrderBook {
        @Getter
        protected LinkedList<OrderModel> buys;

        @Getter
        protected LinkedList<OrderModel> sells;

        @Getter
        protected Map<String, OrderModel> orders = Maps.newHashMap();

        @Getter
        private Long currBlockHeight;

        @Getter
        private int addCnt;

        @Getter
        private int removeCnt;

        public Impl() {}

        // backtrace according to an event
        @Override
        public void revert(OrderEvent event) {
            try {
                OrderModel orderModel;
                EventType type = event.getType();
                OrderLog orderLog = event.getOrderLog();
                switch (type) {
                    case NewOrder:
                        if (orderLog.finished()) {
                            break;
                        }
                        orderModel = orders.get(orderLog.getOrderId());
                        if (orderModel != null) {
                            this.removeOrder(orderModel);
                        } else {
                            System.out.println("[new order] not find order: " + orderLog.getOrderId() + ": "
                                    + new Date(event.getTimestamp() * 1000) + ": " + orderLog.getStatus() + ":"
                                    + event.getBlockHash());
                            // throw new RuntimeException("[new order] not find order: " +
                            // orderLog.getOrderId());
                        }
                        break;
                    case UpdateOrder:
                        if (orderLog.finished()) {
                            orderModel = OrderModel.fromOrderLog(orderLog);
                            this.addOrder(orderModel);
                        } else if (orderLog.getStatus() == OrderStatus.PartialExecuted) {
                            orderModel = orders.get(orderLog.getOrderId());
                            if (orderModel != null) {
                                orderModel.revert(orderLog);
                            } else {
                                System.out.println("[update...] not find order: " + orderLog.getOrderId());
                                // throw new RuntimeException("[new order] not find order: " +
                                // orderLog.getOrderId());
                            }
                        }
                    case TX:
                        break;
                    case Unknown:
                        break;
                    default:
                        throw new AssertionError(type.name());
                }
            } catch (Exception e) {
                log.error("revert failed,the err is :" + e);
            }
        }

        /**
         * update orderBook according to the new event.actually, make the orderBook go
         * forward.
         *
         * @param event the orderLog
         */
        @Override
        public void onward(OrderEvent event) {
            OrderModel orderModel;
            EventType type = event.getType();
            OrderLog orderLog = event.getOrderLog();
            switch (type) {
                case NewOrder:
                    if (!orderLog.finished()) {
                        orderModel = OrderModel.fromOrderLog(orderLog);
                        this.addOrder(orderModel);
                    }
                    break;
                case UpdateOrder:
                    if (orderLog.finished()) {
                        orderModel = orders.get(orderLog.getOrderId());
                        if (orderModel != null) {
                            this.removeOrder(orderModel);
                        }
                    } else if (orderLog.getStatus() == OrderStatus.PartialExecuted) {
                        orderModel = orders.get(orderLog.getOrderId());
                        if (orderModel != null) {
                            orderModel.onward(orderLog);
                        }
                    }
                    break;
                case TX:
                    break;
                case Unknown:
                    break;
                default:
                    throw new AssertionError(type.name());
            }
        }

        private void addOrder(OrderModel orderModel) {
            addCnt++;
            if (orders.containsKey(orderModel.getOrderId())) {
                throw new RuntimeException(String.format("order %s exist", orderModel.getOrderId()));
            }

            orders.put(orderModel.getOrderId(), orderModel);
            if (orderModel.isSide()) { // sell
                this.sells.add(orderModel);
            } else {
                this.buys.add(orderModel);
            }
        }

        private void removeOrder(OrderModel orderModel) {
            removeCnt++;
            if (!orders.containsKey(orderModel.getOrderId())) {
                throw new RuntimeException(String.format("order %s not exist", orderModel.getOrderId()));
            }

            orders.remove(orderModel.getOrderId());
            if (orderModel.isSide()) {
                sells.remove(orderModel);
            } else {
                buys.remove(orderModel);
            }
        }

        @Override
        public void init(List<OrderModel> orderModels, Long blockHeight) {
            // distinct by orderId
            this.buys = Lists.newLinkedList(orderModels.stream().filter(o -> !o.isSide())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(OrderModel::getOrderId))),
                            ArrayList::new)));

            this.sells = Lists.newLinkedList(orderModels.stream().filter(o -> o.isSide())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(OrderModel::getOrderId))),
                            ArrayList::new)));

            this.currBlockHeight = blockHeight;

            Map<String, OrderModel> orderMap = orderModels.stream()
                    .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (o1, o2) -> o1));

            this.orders.putAll(orderMap);
        }



        public OrderBook initFromOrders(List<OrderModel> orders) {
            init(orders, 0l);
            return this;
        }

        @Override
        public BigDecimal getBuy1Price() {
            if (CollectionUtils.isEmpty(this.buys)) {
                return null;
            }
            return this.buys.stream().max(Comparator.comparing(OrderModel::getPrice)).get().getPrice();
        }

        @Override
        public BigDecimal getSell1Price() {
            if (CollectionUtils.isEmpty(this.sells)) {
                return null;
            }
            return this.sells.stream().min(Comparator.comparing(OrderModel::getPrice)).get().getPrice();
        }

        public BigDecimal getAmountSum() {
            BigDecimal amountSum = this.orders.values().stream().map(OrderModel::getAmount).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            return amountSum;
        }

        @Override
        public void revert(BlockEvent event) {
            if (event.getHeight() <= this.currBlockHeight) {
                event.forEach(this, true, true);
                this.currBlockHeight = event.getHeight();
            }
        }

        @Override
        public void onward(BlockEvent event) {
            if (event.getHeight() >= this.currBlockHeight) {
                event.forEach(this, false, false);
                this.currBlockHeight = event.getHeight();
            }
        }

        @Override
        public String hash() {
            String result =
                    orders.values().stream().sorted(Comparator.comparing(OrderModel::getOrderId)).map(t -> t.hash())
                            .collect(Collectors.joining("-"));
            return DigestUtils.md5Hex(result);
        }
    }
}
