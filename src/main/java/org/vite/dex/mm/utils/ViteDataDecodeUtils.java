package org.vite.dex.mm.utils;


import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.utils.decode.BytesUtils;
import org.vite.dex.mm.utils.decode.ViteAddress;
import org.vite.dex.mm.utils.decode.ViteToken;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.ORDER_NEW_EVENT_TOPIC;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.ORDER_UPDATE_EVENT_TOPIC;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.OrderIdBytesLength;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.TX_EVENT_TOPIC;
import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.constant.enums.EventType.TX;
import static org.vite.dex.mm.constant.enums.EventType.Unknown;
import static org.vite.dex.mm.constant.enums.EventType.UpdateOrder;

public class ViteDataDecodeUtils {

    public static String getShowToken(byte[] original) {
        if (original == null || original.length == 0) {
            return "";
        }
        ViteToken token = new ViteToken();
        token.setBytes(original);
        return token.hex();
    }

    public static String getShowAddress(byte[] original) {
        if (original == null || original.length == 0) {
            return "";
        }
        ViteAddress address = new ViteAddress();
        address.setBytes(original);
        return address.hex();
    }

    // get order side by parsing orderId
    public static boolean getOrderSideByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        return (int) idBytes[3] == 1;
    }

    // get order price by parsing orderId
    public static BigDecimal getPriceByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        boolean side = (int) idBytes[3] == 1;
        byte[] price = new byte[10];
        System.arraycopy(idBytes, 4, price, 0, 10);
        if (!side) { // buy
            for (int i = 0; i < price.length; i++) {
                price[i] = (byte) ~price[i];
            }
        }
        return BytesUtils.priceToBigDecimal(price);
    }

    // parsing order create time by parsing orderId
    public static long getOrderCTimeByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        byte[] timestampBytes = new byte[8];
        System.arraycopy(idBytes, 14, timestampBytes, 3, 5);
        long timestamp = bytesToLong(timestampBytes);
        return timestamp;
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes, 0, bytes.length);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static EventType getEventType(List<String> topics) {
        for (String topic : topics) {
            if (TX_EVENT_TOPIC.equals(topic)) {
                return TX;
            }
            if (ORDER_NEW_EVENT_TOPIC.equals(topic)) {
                return NewOrder;
            }
            if (ORDER_UPDATE_EVENT_TOPIC.equals(topic)) {
                return UpdateOrder;
            }
        }
        return Unknown;
    }
}
