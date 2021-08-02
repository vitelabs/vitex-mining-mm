package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

public interface OrderBook {
    // back-trace according to a event
    void revert(OrderEvent event);

    // back-trace according to a event
    void deal(OrderEvent event);

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

        public Impl() {}

        // backtrace according to an event
        public void revert(OrderEvent event) {
            // try {
            //     Vmlog log = e.getVmlog();
            //     byte[] event = log.getData();
            //     EventType eventType = EventParserUtils.getEventType(log.getTopicsRaw());

            //     switch (eventType) {
            //         case NewOrder:
            //             DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
            //             boolean side = dexOrder.getOrder().getSide();
            //             String orderId = Hex.toHexString(dexOrder.getOrder().getId().toByteArray());
            //             revertByRemoveOrder(orderId, side);
            //             break;
            //         case UpdateOrder:
            //             DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
            //             int status = orderUpdateInfo.getStatus();
            //             OrderModel orderModel = OrderModel.assembleOrderByUpdateInfo(orderUpdateInfo);
            //             if (status == OrderUpdateInfoStatus.FullyExecuted.getValue()
            //                     || status == OrderUpdateInfoStatus.Cancelled.getValue()) {
            //                 if (orderModel.isSide()) {
            //                     sells.add(orderModel);
            //                 } else {
            //                     buys.add(orderModel);
            //                 }

            //             } else if (status == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
            //                 BigDecimal executedQuantity =
            //                         new BigDecimal(orderUpdateInfo.getExecutedQuantity().toByteArray().toString());
            //                 BigDecimal executedAmount =
            //                         new BigDecimal((orderUpdateInfo.getExecutedAmount().toByteArray().toString()));
            //                 orderModel.setExecutedAmount(orderModel.getAmount().add(executedAmount));
            //                 orderModel.setExecutedQuantity(orderModel.getQuantity().add(executedQuantity));
            //                 // update current order
            //                 if (orderModel.isSide()) {
            //                     for (int i = 0; i < sells.size(); i++) {
            //                         if (sells.get(i).getId().equals(orderModel.getId())) {
            //                             sells.set(i, orderModel);
            //                         }
            //                     }
            //                 } else {
            //                     for (int i = 0; i < buys.size(); i++) {
            //                         if (buys.get(i).getId().equals(orderModel.getId())) {
            //                             buys.add(orderModel);
            //                         }
            //                     }
            //                 }
            //             }
            //             break;
            //     }
            // } catch (Exception exception) {
            //     log.error("revert failed,the err is :" + exception);
            // }
        }

        private void revertByRemoveOrder(String orderId, boolean side) {
            if (orderId == null) {
                return;
            }

            if (!side) {
                //remove from sells if the new order is sellOrder
                for (int i = 0; i < sells.size(); i++) {
                    if (sells.get(i).getId().equals(orderId)) {
                        sells.remove(i);
                    }
                }
            } else {
                //remove from buys if order is buy_order
                for (int i = 0; i < buys.size(); i++) {
                    if (buys.get(i).getId().equals(orderId)) {
                        buys.remove(i);
                    }
                }
            }
        }

        // back-trace according to a event
        @Override
        public void deal(OrderEvent event) {
            // curEventId == e.id - 1
            // buys.0, sells.0
        }

        @Override
        public void init(List<OrderModel> buys, List<OrderModel> sells) {
            this.buys = Lists.newLinkedList(buys);
            this.sells = Lists.newLinkedList(sells);
        }

        @Override
        public BigDecimal getBuy1Price() {
            if (CollectionUtils.isEmpty(this.buys)) {
                return null;
            }
            return this.buys.getLast().getPrice();
        }

        @Override
        public BigDecimal getSell1Price() {
            if (CollectionUtils.isEmpty(this.sells)) {
                return null;
            }
            return this.sells.getLast().getPrice();
        }
    }
}


