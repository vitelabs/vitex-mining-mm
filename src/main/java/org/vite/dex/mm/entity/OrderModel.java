package org.vite.dex.mm.entity;

import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;

import java.math.BigDecimal;

@Data
public class OrderModel {
    String id;
    String address;
    String tradeToken;
    String quoteToken;
    int marketId;
    boolean side;
    int type;
    int status;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal amount;
    double takerFeeRate;
    double makerFeeRate;
    double takerOperatorFeeRate;
    double makerOperatorFeeRate;
    BigDecimal executedQuantity;
    BigDecimal executedAmount;
    BigDecimal executedBaseFee;
    BigDecimal executedOperatorFee;
    long timestamp;
    String sendHash;

    public static OrderModel assembleOrderByNewInfo(DexTradeEvent.NewOrderInfo newOrderInfo) {
        OrderModel orderModel = new OrderModel();
        orderModel.setId(Hex.toHexString(newOrderInfo.getOrder().getId().toByteArray()));
        orderModel.setAddress(ViteDataDecodeUtils.getShowAddress(newOrderInfo.getOrder().getAddress().toByteArray()));
        orderModel.setTradeToken(ViteDataDecodeUtils.getShowToken(newOrderInfo.getTradeToken().toByteArray()));
        orderModel.setQuoteToken(ViteDataDecodeUtils.getShowToken(newOrderInfo.getQuoteToken().toByteArray()));
        orderModel.setSide(newOrderInfo.getOrder().getSide());
        orderModel.setType(newOrderInfo.getOrder().getType());
        orderModel.setPrice(new BigDecimal(newOrderInfo.getOrder().getPrice().toByteArray().toString()));
        orderModel.setQuantity(new BigDecimal(newOrderInfo.getOrder().getQuantity().toByteArray().toString()));
        orderModel.setAmount(new BigDecimal(newOrderInfo.getOrder().getAmount().toByteArray().toString()));
        orderModel.setStatus(newOrderInfo.getOrder().getStatus());
        orderModel.setTakerFeeRate((long) newOrderInfo.getOrder().getTakerFeeRate());
        orderModel.setMakerFeeRate((long) newOrderInfo.getOrder().getMakerFeeRate());

        return orderModel;
    }

    /**
     * @param orderUpdateInfo TODO can`t assemble order object from update event.how to solve it ?
     * @return
     */
    public static OrderModel assembleOrderByUpdateInfo(DexTradeEvent.OrderUpdateInfo orderUpdateInfo) {
        OrderModel orderModel = new OrderModel();
        orderModel.setId(Hex.toHexString(orderUpdateInfo.getId().toByteArray()));
        orderModel.setStatus(orderUpdateInfo.getStatus());
        return orderModel;
    }

    /**
     * calculate the mm-reward of the order from startTime to endTime
     *
     * @param side
     * @param topPrice
     * @param EffectiveDistance
     * @param startTime
     * @param endTime
     * @return
     */
    public BigDecimal deal(boolean side, BigDecimal topPrice, double EffectiveDistance,
                           long startTime, long endTime) {
        BigDecimal dist = null;
        BigDecimal factor = BigDecimal.ZERO;
        if (side) {
            dist = (getPrice().subtract(topPrice)).divide(topPrice);
        } else {
            dist = (topPrice.subtract(getPrice())).divide(topPrice);
        }

        if (dist.compareTo(new BigDecimal(String.valueOf(EffectiveDistance))) < 0) {
            double coefficient = Math.pow(0.6, (1 + 9 * dist.doubleValue()) / EffectiveDistance);
            factor = (getQuantity().subtract(getExecutedQuantity())).multiply(getPrice())
                    .multiply(BigDecimal.valueOf(endTime - startTime)).multiply(BigDecimal.valueOf(coefficient));
        }
        return factor;
    }
}
