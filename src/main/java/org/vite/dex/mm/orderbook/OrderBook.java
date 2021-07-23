package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.EventParserUtils;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public interface OrderBook {
    // back-trace according to a event
    public void revert(VmLogInfo e);

    // back-trace according to a event
    public void deal(VmLogInfo e);

    // init/**/
    public void init(List<OrderModel> buys, List<OrderModel> sells);

    public List<OrderModel> getBuys();

    public List<OrderModel> getSells();

    public BigDecimal get1BuyPrice();

    public BigDecimal get1SellPrice();

    @Slf4j
    class Impl implements OrderBook {
        @Getter
        protected LinkedList<OrderModel> buys;

        @Getter
        protected LinkedList<OrderModel> sells;

        public Impl() {}

        // back trace according to an event
        public void revert(VmLogInfo e) {
            try {
                Vmlog log = e.getVmlog();
                EventType eventType = EventParserUtils.getEventType(log.getTopicsRaw());
                byte[] event = log.getData();

                switch (eventType) {
                    case NewOrder:
                        DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                        boolean side = dexOrder.getOrder().getSide();
                        String orderId = Hex.toHexString(dexOrder.getOrder().getId().toByteArray());
                        revertByRemoveOrder(orderId, side);
                        break;
                    case UpdateOrder:
                        DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                        // 1:pending 2:PartialExecuted 3:FullyExecuted 4:Cancelled
                        int status = orderUpdateInfo.getStatus();
                        Long tableCode = 345L;
                        OrderModel orderModel = null;
                        Optional<OrderModel> dexOrderUpdate =
                                queryNewOrder(Hex.toHexString(orderUpdateInfo.getId().toByteArray()), tableCode);
                        if (!dexOrderUpdate.isPresent()) {
                            return;
                        }
                        orderModel = dexOrderUpdate.get();

                        if (status == OrderUpdateInfoStatus.FullyExecuted.getValue()
                                || status == OrderUpdateInfoStatus.Cancelled.getValue()) {
                            if (orderModel.isSide()) {
                                sells.add(orderModel);
                            } else {
                                buys.add(orderModel);
                            }

                        } else if (status == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                            BigDecimal executedQuantity =
                                    new BigDecimal(orderUpdateInfo.getExecutedQuantity().toByteArray().toString());
                            BigDecimal executedAmount =
                                    new BigDecimal((orderUpdateInfo.getExecutedAmount().toByteArray().toString()));
                            orderModel.setExecutedAmount(orderModel.getAmount().add(executedAmount));
                            orderModel.setExecutedQuantity(orderModel.getQuantity().add(executedQuantity));
                            // todo update current order
                            if (orderModel.isSide()) {
                                sells.add(orderModel);
                            } else {
                                buys.add(orderModel);
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

        /**
         * query the order item from chain.dex_new_order table
         *
         * @param orderId
         * @param tableCode the sub table code
         * @return
         */
        public Optional<OrderModel> queryNewOrder(String orderId, long tableCode) {
            OrderModel dexOrder = new OrderModel();
            // TODO assemble the order object
            //            dexOrder.setId("123456");
            //            if (CollectionUtils.isNotEmpty(dexNewOrders)) {
            //                OrderModel dexOrder = new OrderModel();
            //                dexOrder.setId("aaa");
            //                dexOrder.setOrderId(dexNewOrders.get(0).getOrderId());
            //                dexOrder.setAddress(dexNewOrders.get(0).getAddress());
            //                dexOrder.setTradeToken(dexNewOrders.get(0).getTradeTokenId());
            //                dexOrder.setQuoteToken(dexNewOrders.get(0).getQuoteTokenId());
            //                dexOrder.setSide(dexNewOrders.get(0).getOrderSide() == 1);
            //                dexOrder.setOrderType(dexNewOrders.get(0).getOrderType());
            //                dexOrder.setPrice(dexNewOrders.get(0).getPrice().toPlainString());
            //                dexOrder.setQuantity(dexNewOrders.get(0).getQuantity().toPlainString());
            //                dexOrder.setToAmount(dexNewOrders.get(0).getAmount().toPlainString());
            //                dexOrder.setOrderTime(dexNewOrders.get(0).getOrderTime());
            //                dexOrder.setTradeTokenDecimals(dexNewOrders.get(0).getTradeTokenDecimal());
            //                dexOrder.setQuoteTokenDecimals(dexNewOrders.get(0).getQuoteTokenDecimal());
            //                dexOrder.setLockedBuyFee(dexNewOrders.get(0).getLockedBuyFee().toPlainString());
            //                dexOrder.setMakerFeeRate(dexNewOrders.get(0).getMakerFeeRate());
            //                dexOrder.setTakerFeeRate(dexNewOrders.get(0).getTakerFeeRate());
            //                dexOrder.setTakerBrokerFeeRate(dexNewOrders.get(0).getTakerBrokerFeeRate());
            //                dexOrder.setMakerBrokerFeeRate(dexNewOrders.get(0).getMakerBrokerFeeRate());
            //                dexOrder.setOrderHash(dexNewOrders.get(0).getOrderHash());
            //                dexOrder.setAgent(dexNewOrders.get(0).getAgent());
            return Optional.of(dexOrder);
        }


        // back-trace according to a event
        @Override
        public void deal(VmLogInfo e) {
            // curEventId == e.id - 1
            // buys.0, sells.0
        }

        @Override
        public void init(List<OrderModel> buys, List<OrderModel> sells) {
            // todo
            this.buys = Lists.newLinkedList(buys);
            this.sells = Lists.newLinkedList(sells);
        }



        @Override
        public BigDecimal get1BuyPrice() {
            if (CollectionUtils.isEmpty(this.buys)) {
                return null;
            }
            return this.buys.getLast().getPrice();
        }

        @Override
        public BigDecimal get1SellPrice() {
            if (CollectionUtils.isEmpty(this.sells)) {
                return null;
            }
            return this.sells.getLast().getPrice();
        }
    }
}


