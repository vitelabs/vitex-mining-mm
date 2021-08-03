package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderLog;
import org.vite.dex.mm.entity.OrderModel;

import java.math.BigDecimal;
import java.util.*;
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

    BigDecimal getBuy1Price();

    BigDecimal getSell1Price();

    @Slf4j
    class Impl implements OrderBook {
        @Getter
        protected LinkedList<OrderModel> buys;

        @Getter
        protected LinkedList<OrderModel> sells;

        protected Map<String, OrderModel> orders = new HashMap<>();

        public Impl() {
        }

        // backtrace according to an event
        public void revert(OrderEvent event) {
            try {
                OrderEventType type = event.getType();
                OrderLog orderLog = event.getOrderLog();
                switch (type) {
                    case OrderNew:
                        revertByRemoveOrder(orderLog.getOrderId(), orderLog.isSide());
                        break;
                    case OrderUpdate:
                        if (orderLog.getStatus() == OrderUpdateInfoStatus.FullyExecuted.getValue()
                                || orderLog.getStatus() == OrderUpdateInfoStatus.Cancelled.getValue()) {
                            OrderModel orderModel = OrderModel.fromOrderLog(orderLog);
                            if (orderLog.isSide()) {
                                sells.add(orderModel);
                            } else {
                                buys.add(orderModel);
                            }

                        } else if (orderLog.getStatus() == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                            // update current order
                            for (int i = 0; i < sells.size(); i++) {
                                OrderModel order = sells.get(i);
                                if (order.getOrderId().equals(orderLog.getOrderId())) {
                                    order.revert(orderLog);
                                }
                            }
                            for (int i = 0; i < buys.size(); i++) {
                                OrderModel order = buys.get(i);
                                if (order.getOrderId().equals(orderLog.getOrderId())) {
                                    order.revert(orderLog);
                                }
                            }
                        }
                        break;
                }
            } catch (Exception exception) {
                log.error("revert failed,the err is :" + exception);
            }
        }

        private void revertByRemoveOrder(String orderId, boolean side) {
            if (orderId == null) {
                return;
            }

            if (!side) {
                //remove from sells if the new order is sellOrder
                for (int i = 0; i < sells.size(); i++) {
                    if (sells.get(i).getOrderId().equals(orderId)) {
                        sells.remove(i);
                    }
                }
            } else {
                //remove from buys if order is buy_order
                for (int i = 0; i < buys.size(); i++) {
                    if (buys.get(i).getOrderId().equals(orderId)) {
                        buys.remove(i);
                    }
                }
            }
        }

        // front-trace according to a event
        @Override
        public void onward(OrderEvent event) {
            OrderModel orderModel;
            OrderEventType type = event.getType();
            OrderLog orderLog = event.getOrderLog();
            switch (type) {
                case OrderNew:
                    orderModel = OrderModel.fromOrderLog(event.getOrderLog());
                    orders.put(orderModel.getOrderId(), orderModel);
                    if (orderModel.isSide()) { // sell
                        this.sells.add(orderModel);
                    } else {
                        this.buys.add(orderModel);
                    }
                    break;
                case OrderUpdate:
                    if (orderLog.getStatus() == OrderUpdateInfoStatus.FullyExecuted.getValue()
                            || orderLog.getStatus() == OrderUpdateInfoStatus.Cancelled.getValue()) {
                        orderModel = orders.get(orderLog.getOrderId());
                        orderModel.onward(orderLog);
                        orders.remove(orderLog.getOrderId());
                        if (orderLog.isSide()) {
                            sells.remove(orderModel);
                        } else {
                            buys.remove(orderModel);
                        }
                    } else if (orderLog.getStatus() == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                        // update current order
                        orderModel = orders.get(orderLog.getOrderId());
                        orderModel.onward(orderLog);
                    }
                    break;
            }
        }

        @Override
        public void init(List<OrderModel> buys, List<OrderModel> sells) {
            this.buys = Lists.newLinkedList(buys);
            this.sells = Lists.newLinkedList(sells);
            Map<String, OrderModel> buyMap = buys.stream().collect(Collectors.toMap(OrderModel::getOrderId, o -> o));
            Map<String, OrderModel> sellMap = sells.stream().collect(Collectors.toMap(OrderModel::getOrderId, o -> o));
            this.orders.putAll(buyMap);
            this.orders.putAll(sellMap);
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
    }
}


