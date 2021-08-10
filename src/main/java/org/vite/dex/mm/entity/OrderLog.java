package org.vite.dex.mm.entity;

import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.decode.DexPrice;

import java.math.BigDecimal;

import static org.vite.dex.mm.constant.constants.MMConst.UnderscoreStr;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.*;

@Data
public class OrderLog {
    private String logId;  //optional
    private Long orderCreateTime;
    private String orderId;
    private BigDecimal price;
    private BigDecimal changeAmount;   // for order book
    private BigDecimal changeQuantity; // for order book
    private boolean side;
    private String tradePair;
    private String address;  // TODO: need to fix
    private int status;

    public static OrderLog fromNewOrder(DexTradeEvent.NewOrderInfo dexOrder) {
        OrderLog result = new OrderLog();
        byte[] orderIdBytes = dexOrder.getOrder().getId().toByteArray();
        result.setOrderId(Hex.toHexString(orderIdBytes));
        result.setSide(dexOrder.getOrder().getSide());
        String tradeToken = ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        //todo should process the unit of price、amount and quantity
        result.setChangeQuantity(DexPrice.bytesToBigDecimal(dexOrder.getOrder().getQuantity().toByteArray()));
        result.setChangeAmount(DexPrice.bytesToBigDecimal(dexOrder.getOrder().getAmount().toByteArray()));
        result.setAddress(ViteDataDecodeUtils.getShowAddress(dexOrder.getOrder().getAddress().toByteArray()));
        result.setPrice(DexPrice.bytesToBigDecimal(dexOrder.getOrder().getPrice().toByteArray()));
        result.setOrderCreateTime(getOrderCTimeByParseOrderId(orderIdBytes));
        return result;
    }

    public static OrderLog fromUpdateOrder(DexTradeEvent.OrderUpdateInfo orderUpdateInfo) {
        OrderLog result = new OrderLog();
        byte[] orderIdBytes = orderUpdateInfo.getId().toByteArray();
        String orderId = Hex.toHexString(orderIdBytes);
        result.setOrderId(orderId);
        String tradeToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
        String quoteToken = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
        result.setTradePair(tradeToken + UnderscoreStr + quoteToken);
        //todo should process the unit of price、amount and quantity
        result.setChangeQuantity(DexPrice.bytesToBigDecimal(orderUpdateInfo.getExecutedQuantity().toByteArray()));
        result.setChangeAmount(DexPrice.bytesToBigDecimal(orderUpdateInfo.getExecutedAmount().toByteArray()));
        result.setAddress(""); // TODO: a little difficult
        result.setPrice(getPriceByParseOrderId(orderIdBytes));
        result.setSide(getOrderSideByParseOrderId(orderIdBytes));
        result.setStatus(orderUpdateInfo.getStatus());
        result.setOrderCreateTime(getOrderCTimeByParseOrderId(orderIdBytes));
        return result;
    }
}
