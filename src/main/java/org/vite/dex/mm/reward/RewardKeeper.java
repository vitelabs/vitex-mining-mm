package org.vite.dex.mm.reward;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.constant.enums.QuoteMarketType;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.bean.MiningRewardCfg;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.vite.dex.mm.constant.constants.MMConst.OrderIdBytesLength;
import static org.vite.dex.mm.utils.EventParserUtils.getEventType;
import static org.vite.dex.mm.utils.decode.DexPrice.bytes2Double;

@Service
public class RewardKeeper {
    private static final Logger logger = LoggerFactory.getLogger(RewardKeeper.class);
    private Map<String, RewardOrder> orderRewards = new HashMap<>();

    @Autowired
    private TradeRecover tradeRecover;

    /**
     * calculate the mm-mining of pending order which located in the specified orderBook
     *
     * @param eventStream
     * @param originOrderBook
     * @param cfg
     * @return
     */
    public Map<String, RewardOrder> mmMining(EventStream eventStream, OrderBook originOrderBook, MiningRewardCfg cfg,
            long endTime) {
        List<VmLogInfo> events = eventStream.getEvents();
        Collections.reverse(events);

        for (VmLogInfo e : events) {
            List<OrderModel> buys = originOrderBook.getBuys();
            List<OrderModel> sells = originOrderBook.getSells();
            BigDecimal sell1Price = originOrderBook.get1SellPrice();
            BigDecimal buy1Price = originOrderBook.get1BuyPrice();
            // when an event coming, update the orderBook and top1 price
            try {
                Vmlog log = e.getVmlog();
                EventType eventType = getEventType(log.getTopicsRaw());
                byte[] event = log.getData();

                switch (eventType) {
                    case NewOrder:
                        DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                        OrderModel orderModel = new OrderModel();
                        orderModel.setId(dexOrder.getOrder().getId().toString());
                        orderModel.setAddress(
                                ViteDataDecodeUtils.getShowAddress(dexOrder.getOrder().getAddress().toByteArray()));
                        orderModel.setSide(dexOrder.getOrder().getSide()); // false buy, true sell

                        // todo event.timestamp
                        if (dexOrder.getOrder().getTimestamp() > endTime) {
                            continue;
                        }

                        if (dexOrder.getOrder().getSide()) {
                            // the new order is sell order
                            for (OrderModel model : buys) {
                                BigDecimal dist = (model.getPrice().subtract(buy1Price)).divide(buy1Price);
                                if (dist.compareTo(new BigDecimal(String.valueOf(cfg.getEffectiveDistance()))) < 0) {
                                    // todo move reward logic to reward order
                                    RewardOrder r = orderRewards.get(model.getId());
                                    double factor =
                                            Math.pow(0.6, (1 + 9 * dist.doubleValue()) / cfg.getEffectiveDistance());
                                    double total = r.getOrder().getQuantity() * (r.getOrder().getPrice())
                                            * (model.getTimestamp() - r.getLastCalculatedTime()) * factor;
                                    orderRewards.put(model.getId(),
                                            addRewardToDiffMarket(r, total, model.getTimestamp()));
                                }
                            }
                            sells.add(orderModel);
                        } else {
                            // the new order is buy order

                            for (OrderModel model : sells) {
                                BigDecimal dist = (sell1Price.subtract(model.getPrice())).divide(sell1Price);
                                if (dist.compareTo(new BigDecimal(String.valueOf(cfg.getEffectiveDistance()))) < 0) {
                                    RewardOrder r = orderRewards.get(model.getId());
                                    double factor =
                                            Math.pow(0.6, (1 + 9 * dist.doubleValue()) / cfg.getEffectiveDistance());
                                    double total = r.getOrder().getQuantity() * (r.getOrder().getPrice())
                                            * (model.getTimestamp() - r.getLastCalculatedTime()) * factor;
                                    orderRewards.put(model.getId(),
                                            addRewardToDiffMarket(r, total, model.getTimestamp()));
                                }
                            }
                            buys.add(orderModel);
                        }
                        break;

                    case UpdateOrder:
                        DexTradeEvent.OrderUpdateInfo dOrder = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                        OrderModel om = new OrderModel();
                        int status = dOrder.getStatus();
                        boolean side = getOrderSideByParseOrderId(dOrder.getId().toByteArray());
                        // todo only canceled event should be cal reward
                        if (status == OrderUpdateInfoStatus.Cancelled.getValue() ||
                                status == OrderUpdateInfoStatus.FullyExecuted.getValue()) {
                            for (OrderModel model : sells) {
                                BigDecimal dist = (model.getPrice().subtract(buy1Price)).divide(buy1Price);
                                if (dist.compareTo(new BigDecimal(String.valueOf(cfg.getEffectiveDistance()))) < 0) {
                                    RewardOrder r = orderRewards.get(model.getId());
                                    double factor =
                                            Math.pow(0.6, (1 + 9 * dist.doubleValue()) / cfg.getEffectiveDistance());
                                    double total = r.getOrder().getQuantity() * (r.getOrder().getPrice())
                                            * (model.getTimestamp() - r.getLastCalculatedTime()) * factor;
                                    orderRewards.put(model.getId(),
                                            addRewardToDiffMarket(r, total, model.getTimestamp()));
                                }
                            }

                            for (OrderModel model : buys) {
                                BigDecimal dist = (sell1Price.subtract(model.getPrice())).divide(sell1Price);
                                if (dist.compareTo(new BigDecimal(String.valueOf(cfg.getEffectiveDistance()))) < 0) {
                                    RewardOrder r = orderRewards.get(model.getId());
                                    double factor =
                                            Math.pow(0.6, (1 + 9 * dist.doubleValue()) / cfg.getEffectiveDistance());
                                    double total = r.getOrder().getQuantity() * (r.getOrder().getPrice())
                                            * (model.getTimestamp() - r.getLastCalculatedTime()) * factor;
                                    orderRewards.put(model.getId(),
                                            addRewardToDiffMarket(r, total, model.getTimestamp()));
                                }
                            }
                            if (!side) {
                                sells.remove(om);
                            } else {
                                buys.remove(om);
                            }

                        } else if (status == OrderUpdateInfoStatus.PartialExecuted.getValue()) {
                            // just update order quantity,have no effect on top1 price
                            for (OrderModel model : buys) {
                                if (model.getId().equals(om.getId())) {
                                    RewardOrder r = orderRewards.get(model.getId());
                                    r.getOrder().setQuantity(bytes2Double(dOrder.getExecutedQuantity().toByteArray()));
                                }
                            }
                            for (OrderModel model : sells) {
                                if (model.getId().equals(om.getId())) {
                                    RewardOrder r = orderRewards.get(model.getId());
                                    r.getOrder().setQuantity(bytes2Double(dOrder.getExecutedQuantity().toByteArray()));
                                }
                            }
                        }

                        break;
                }
            } catch (Exception ex) {
                logger.error("mmMining occurs error,the err info: ", ex.getMessage());
            }
        }
        // todo recal reward

        return orderRewards;
    }

    // parsing orderId and get order side
    private boolean getOrderSideByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        return (int) idBytes[3] == 1;
    }

    /**
     * Market needs to be differentiated
     *
     * @param r
     * @param total
     * @param timestamp
     * @return
     */
    private RewardOrder addRewardToDiffMarket(RewardOrder r, double total, long timestamp) {
        if (r.getMarket() == QuoteMarketType.USDTMarket.getValue()) {
            r.getOrder().setMmRewardForBTCMarket(
                    r.getOrder().getMmRewardForBTCMarket().add(new BigDecimal(Double.toString(total))));
        } else if (r.getMarket() == QuoteMarketType.BTCMarket.getValue()) {
            r.getOrder().setMmRewardForETHMarket(
                    r.getOrder().getMmRewardForETHMarket().add(new BigDecimal(Double.toString(total))));
        } else if (r.getMarket() == QuoteMarketType.ETHMarket.getValue()) {
            r.getOrder().setMmRewardForVITEMarket(
                    r.getOrder().getMmRewardForVITEMarket().add(new BigDecimal(Double.toString(total))));
        } else {
            r.getOrder().setMmRewardForUSDTMarket(
                    r.getOrder().getMmRewardForUSDTMarket().add(new BigDecimal(Double.toString(total))));
        }
        r.setLastCalculatedTime(timestamp);
        return r;
    }

    public void start(long start, long end) {

    }
}
