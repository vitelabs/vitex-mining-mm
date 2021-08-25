package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface OrderBook {
    // back-trace according to a event
    void revert(OrderEvent event);

    // front-trace according to a event
    void onward(OrderEvent event);

    // init method
    void init(List<OrderModel> buys, List<OrderModel> sells);

    List<OrderModel> getBuys();

    List<OrderModel> getSells();

    Map<String, OrderModel> getOrders(); // <OrderId,OrderModel>

    BigDecimal getBuy1Price();

    BigDecimal getSell1Price();

    int getAddCnt();

    int getRemoveCnt();

    @Slf4j
    class Impl implements OrderBook {
        @Getter
        protected LinkedList<OrderModel> buys;

        @Getter
        protected LinkedList<OrderModel> sells;

        @Getter
        protected Map<String, OrderModel> orders = Maps.newHashMap();

        @Getter
        private int addCnt;

        @Getter
        private int removeCnt;

        public Impl() {
        }

        // backtrace according to an event
        @Override
        public void revert(OrderEvent event) {
            try {
                OrderModel orderModel;
                EventType type = event.getType();
                OrderLog orderLog = event.getOrderLog();
                switch (type) {
                case NewOrder:
                    orderModel = orders.get(orderLog.getOrderId());
                    if (orderModel != null) {
                        this.removeOrder(orderModel);
                    }
                    break;
                case UpdateOrder:
                    if (orderLog.getStatus() == OrderUpdateInfoStatus.FullyExecuted.getValue()
                            || orderLog.getStatus() == OrderUpdateInfoStatus.Cancelled.getValue()) {
                        orderModel = OrderModel.fromOrderLog(orderLog);
                        this.addOrder(orderModel);
                    } else if (orderLog.getStatus() == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                        orderModel = orders.get(orderLog.getOrderId());
                        if (orderModel != null) {
                            orderModel.revert(orderLog);
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
            org.vite.dex.mm.constant.enums.EventType type = event.getType();
            OrderLog orderLog = event.getOrderLog();
            switch (type) {
            case NewOrder:
                orderModel = OrderModel.fromOrderLog(orderLog);
                this.addOrder(orderModel);
                break;
            case UpdateOrder:
                if (orderLog.getStatus() == OrderUpdateInfoStatus.FullyExecuted.getValue()
                        || orderLog.getStatus() == OrderUpdateInfoStatus.Cancelled.getValue()) {
                    orderModel = orders.get(orderLog.getOrderId());
                    this.removeOrder(orderModel);
                } else if (orderLog.getStatus() == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                    // update current order
                    orderModel = orders.get(orderLog.getOrderId());
                    orderModel.onward(orderLog);
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
            if (orders.containsKey(orderModel.getOrderId())) {
                throw new RuntimeException(String.format("order %s exist", orderModel.getOrderId()));
            }
            addCnt++;
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
        public void init(List<OrderModel> buys, List<OrderModel> sells) {
            this.buys = Lists.newLinkedList(buys);
            this.sells = Lists.newLinkedList(sells);
            Map<String, OrderModel> buyMap = buys.stream()
                    .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (o1, o2) -> o1));
            Map<String, OrderModel> sellMap = sells.stream()
                    .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (o1, o2) -> o1));
            this.orders.putAll(buyMap);
            this.orders.putAll(sellMap);
        }

        @Override
        public BigDecimal getBuy1Price() {
            if (CollectionUtils.isEmpty(this.buys)) {
                return null;
            }
            return this.buys.stream().max(Comparator.comparing(OrderModel::getPrice)).get().getPrice();
            // return this.buys.get(0).getPrice();
        }

        @Override
        public BigDecimal getSell1Price() {
            if (CollectionUtils.isEmpty(this.sells)) {
                return null;
            }
            return this.sells.stream().min(Comparator.comparing(OrderModel::getPrice)).get().getPrice();
        }
    }
}
