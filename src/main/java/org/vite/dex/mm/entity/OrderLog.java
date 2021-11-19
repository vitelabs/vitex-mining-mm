package org.vite.dex.mm.entity;

import com.google.protobuf.ByteString;
import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.decode.BytesUtils;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.vite.dex.mm.constant.constants.MiningConst.UnderscoreStr;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getOrderCTimeByParseOrderId;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getOrderSideByParseOrderId;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getPriceByParseOrderId;

@Data
public class OrderLog {
    private String orderId;
    private Long orderCreateTime;
    private BigDecimal price;
    private BigDecimal changeQuantity;
    private BigDecimal changeAmount;
    private boolean side;
    private String tradePair;
    private String address;
    private OrderStatus status;
    private Vmlog rawLog;

    public OrderLog(String tradePair) {
        this.tradePair = tradePair;
    }

    public OrderLog() {}

    public static OrderLog fromNewOrder(DexTradeEvent.NewOrderInfo dexOrder, Vmlog vmlog) {
        OrderLog result = new OrderLog();
        DexTradeEvent.Order order = dexOrder.getOrder();
        byte[] orderIdBytes = order.getId().toByteArray();
        result.setOrderId(Hex.toHexString(orderIdBytes));
        result.setSide(order.getSide());
        String tradeToken = ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        result.setChangeQuantity(sub(order.getQuantity(), order.getExecutedQuantity()));
        result.setChangeAmount(sub(order.getAmount(), order.getExecutedAmount()));
        result.setAddress(ViteDataDecodeUtils.getShowAddress(order.getAddress().toByteArray()));
        result.setPrice(BytesUtils.priceToBigDecimal(order.getPrice().toByteArray()));
        result.setOrderCreateTime(getOrderCTimeByParseOrderId(orderIdBytes));
        result.setStatus(OrderStatus.of(order.getStatus()));
        result.rawLog = vmlog;

        return result;
    }

    // both cancel and filled order will emit the updateEvent
    public static OrderLog fromUpdateOrder(DexTradeEvent.OrderUpdateInfo orderUpdateInfo, Vmlog vmlog, OrderTx tx,
            Tokens tokens) {
        OrderLog result = new OrderLog();
        byte[] orderIdBytes = orderUpdateInfo.getId().toByteArray();
        String orderId = Hex.toHexString(orderIdBytes);
        result.setOrderId(orderId);
        result.setStatus(OrderStatus.of(orderUpdateInfo.getStatus()));
        result.setSide(getOrderSideByParseOrderId(orderIdBytes));
        String tradeToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        result.setPrice(getPriceByParseOrderId(orderIdBytes));
        long orderCreateTime = getOrderCTimeByParseOrderId(orderIdBytes);
        result.setOrderCreateTime(orderCreateTime);
        result.rawLog = vmlog;

        if (result.getStatus() == OrderStatus.Cancelled) {
            int tradeDecimals = tokens.getDecimals(tradeToken);
            int quoteDecimals = tokens.getDecimals(quoteToken);

            if (result.isSide()) {
                // real quantity
                result.setChangeQuantity(
                        BytesUtils.quantityToBigDecimal(orderUpdateInfo.getRefundQuantity().toByteArray()));
                BigDecimal rawAmount = calculateRawAmount(result.getChangeQuantity(), result.getPrice(),
                        tradeDecimals - quoteDecimals);
                result.setChangeAmount(rawAmount);
            } else {
                // the refund quantity is actually RefundAmount
                result.setChangeAmount(
                        BytesUtils.quantityToBigDecimal(orderUpdateInfo.getRefundQuantity().toByteArray()));
                BigDecimal rawQuantity = calculateRawQuantity(result.getChangeAmount(), result.getPrice(),
                        tradeDecimals - quoteDecimals);
                result.setChangeQuantity(rawQuantity);
            }
        } else {
            result.setChangeQuantity(tx.getQuantity());
            result.setChangeAmount(tx.getAmount());
        }

        return result;
    }

    private static BigDecimal calculateRawQuantity(BigDecimal amount, BigDecimal price, int decimalsDiff) {
        BigDecimal rawAmount = amount.divide(price, 18, RoundingMode.DOWN);
        if (decimalsDiff == 0) {
            return rawAmount;
        } else if (decimalsDiff > 0) {
            return rawAmount.multiply(new BigDecimal(10).pow(decimalsDiff));
        } else {
            return rawAmount.divide(new BigDecimal(10).pow(Math.abs(decimalsDiff)), 18, RoundingMode.DOWN);
        }
    }

    private static BigDecimal calculateRawAmount(BigDecimal quantity, BigDecimal price, int decimalsDiff) {
        return adjustForDecimalsDiff(quantity.multiply(price), decimalsDiff);
    }

    private static BigDecimal adjustForDecimalsDiff(BigDecimal sourceAmount, int decimalsDiff) {
        if (decimalsDiff == 0) {
            return sourceAmount;
        } else if (decimalsDiff > 0) {
            return sourceAmount.divide(new BigDecimal(10).pow(decimalsDiff), 18, RoundingMode.DOWN);
        } else {
            return sourceAmount.multiply(new BigDecimal(10).pow(Math.abs(decimalsDiff)));
        }
    }

    private static BigDecimal sub(ByteString q1, ByteString q2) {
        BigDecimal b1 = BytesUtils.quantityToBigDecimal(q1.toByteArray());
        BigDecimal b2 = BytesUtils.quantityToBigDecimal(q2.toByteArray());
        return b1.subtract(b2);
    }

    public boolean finished() {
        return this.getStatus() == OrderStatus.FullyExecuted || this.getStatus() == OrderStatus.Cancelled;
    }
}
