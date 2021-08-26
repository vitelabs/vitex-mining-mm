package org.vite.dex.mm.entity;

import com.google.protobuf.ByteString;
import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.decode.DexPrice;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.UnderscoreStr;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.UsdDecimal;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getOrderCTimeByParseOrderId;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getOrderSideByParseOrderId;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getPriceByParseOrderId;

@Data
public class OrderLog {
    private String logId; // optional
    private Long orderCreateTime;
    private String orderId;
    private BigDecimal price;
    private BigDecimal changeAmount; // for order book
    private BigDecimal changeQuantity; // for order book
    private boolean side;
    private String tradePair;
    private String address;
    private OrderStatus status;
    private Vmlog rawLog;

    public static OrderLog fromNewOrder(Vmlog vmlog, DexTradeEvent.NewOrderInfo dexOrder) {
        OrderLog result = new OrderLog();
        DexTradeEvent.Order order = dexOrder.getOrder();
        byte[] orderIdBytes = dexOrder.getOrder().getId().toByteArray();
        result.setOrderId(Hex.toHexString(orderIdBytes));
        result.setSide(dexOrder.getOrder().getSide());
        String tradeToken = ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        result.setChangeQuantity(sub(order.getQuantity(), order.getExecutedQuantity()));
        result.setChangeAmount(sub(order.getAmount(), order.getExecutedAmount()));
        result.setAddress(ViteDataDecodeUtils.getShowAddress(dexOrder.getOrder().getAddress().toByteArray()));
        // the price must be converted
        result.setPrice(DexPrice.bytesToBigDecimal(dexOrder.getOrder().getPrice().toByteArray())
                .multiply(new BigDecimal(UsdDecimal)).setScale(12, RoundingMode.DOWN));
        result.setOrderCreateTime(getOrderCTimeByParseOrderId(orderIdBytes));
        result.setStatus(OrderStatus.of(dexOrder.getOrder().getStatus()));
        result.rawLog = vmlog;

        return result;
    }

    public static OrderLog fromUpdateOrder(Vmlog vmlog, DexTradeEvent.OrderUpdateInfo orderUpdateInfo) {
        OrderLog result = new OrderLog();
        byte[] orderIdBytes = orderUpdateInfo.getId().toByteArray();
        String orderId = Hex.toHexString(orderIdBytes);
        result.setOrderId(orderId);
        String tradeToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        result.setChangeQuantity(DexPrice.bytesToBigDecimal(orderUpdateInfo.getExecutedQuantity().toByteArray()));
        result.setChangeAmount(DexPrice.bytesToBigDecimal(orderUpdateInfo.getExecutedAmount().toByteArray()));
        // the price must be converted
        result.setPrice(getPriceByParseOrderId(orderIdBytes).multiply(new BigDecimal(UsdDecimal)).setScale(12,
                RoundingMode.DOWN));
        result.setSide(getOrderSideByParseOrderId(orderIdBytes));
        result.setStatus(OrderStatus.of(orderUpdateInfo.getStatus()));
        long orderCreateTime = getOrderCTimeByParseOrderId(orderIdBytes);
        result.setOrderCreateTime(orderCreateTime);
        result.rawLog = vmlog;
        
        return result;
    }

    private static BigDecimal sub(ByteString q1, ByteString q2) {
        BigDecimal b1 = DexPrice.bytesToBigDecimal(q1.toByteArray());
        BigDecimal b2 = DexPrice.bytesToBigDecimal(q2.toByteArray());
        return b1.subtract(b2);
    }

    public boolean finished() {
        return this.getStatus() == OrderStatus.FullyExecuted || this.getStatus() == OrderStatus.Cancelled;
    }
}
