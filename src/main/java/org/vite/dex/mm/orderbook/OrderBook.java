package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.model.bean.BlockEvent;
import org.vite.dex.mm.model.bean.OrderEvent;
import org.vite.dex.mm.model.bean.OrderLog;
import org.vite.dex.mm.model.bean.OrderModel;
import org.vite.dex.mm.orderbook.ifaces.IBlockEventHandler;
import org.vite.dex.mm.orderbook.ifaces.IOrderBook;
import org.vite.dex.mm.orderbook.ifaces.IOrderEventHandleAware;
import org.vite.dex.mm.orderbook.ifaces.IOrderEventHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
public class OrderBook implements IOrderBook {
    @Getter
    protected LinkedList<OrderModel> buys;

    @Getter
    protected LinkedList<OrderModel> sells;

    @Getter
    protected Map<String, OrderModel> orders = Maps.newHashMap();

    @Getter
    private Long currBlockHeight;

    // true -> onward, false -> revert
    private boolean lastAction = true;

    @Getter
    private int addCnt;

    @Getter
    private int removeCnt;

    private IOrderEventHandleAware aware;

    public OrderBook() {}

    // backtrace according to BlockEvent
    @Override
    public void revert(BlockEvent event) {
        if (validAction(false, event.getHeight())) {
            event.travel(this, true, true);
            this.currBlockHeight = event.getHeight();
            this.lastAction = false;
        }
    }

    // make the orderBook go onward according to BlockEvent
    @Override
    public void onward(BlockEvent event) {
        if (validAction(true, event.getHeight())) {
            event.travel(this, false, false);
            this.currBlockHeight = event.getHeight();
            this.lastAction = true;
        }
    }

    // backtrace according to an OrderEvent
    @Override
    public void revert(OrderEvent event) {
        if (aware == null) {
            revertInternal(event);
        } else {
            aware.beforeRevert(this, event);
            revertInternal(event);
            aware.afterRevert(this, event);
        }
    }

    // make the orderBook go onward and execute aspect logic(calc Reward) according
    // to an OrderEvent
    @Override
    public void onward(OrderEvent event) {
        if (aware == null) {
            onwardInternal(event);
        } else {
            aware.beforeOnward(this, event);
            onwardInternal(event);
            aware.aferOnward(this, event);
        }
    }

    public void revertInternal(OrderEvent event) {
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
                    log.error("[new order] not find while revert, orderId {} , orderStatus {}, blockHash {}",
                            orderLog.getOrderId(),
                            orderLog.getStatus(), event.getBlockHash());
                    throw new RuntimeException("[new order] not find order: " + orderLog.getOrderId());
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
                        log.error("[update...] not find order while revert, orderId {}", orderLog.getOrderId());
                        throw new RuntimeException("[update order] not find order: " + orderLog.getOrderId());
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

    private void onwardInternal(OrderEvent event) {
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
        // distinct
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

    public OrderBook initFromOrders(List<OrderModel> orders, Long blockHeight) {
        init(orders, blockHeight);
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
    public void setOrderAware(IOrderEventHandleAware aware) {
        this.aware = aware;
    }

    @Override
    public String hash() {
        String result = orders.values().stream().sorted(Comparator.comparing(OrderModel::getOrderId))
                .map(t -> t.hash()).collect(Collectors.joining("-"));
        return DigestUtils.md5Hex(result);
    }

    private boolean validAction(boolean onward, long height) {
        if (onward) {
            if (lastAction) {
                return height > this.currBlockHeight;
            } else {
                return height >= this.currBlockHeight;
            }
        } else {
            if (!lastAction) {
                return height < this.currBlockHeight;
            } else {
                return height <= this.currBlockHeight;
            }
        }
    }
}
