package org.vite.dex.mm.utils;


import org.vite.dex.mm.utils.decode.DexPrice;
import org.vite.dex.mm.utils.decode.ViteAddress;
import org.vite.dex.mm.utils.decode.ViteToken;

import java.math.BigDecimal;

import static org.vite.dex.mm.constant.constants.MMConst.OrderIdBytesLength;

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

    // parsing orderId and get order side
    public static boolean getOrderSideByParseOrderId(byte[] idBytes) {
        if (idBytes.length != OrderIdBytesLength) {
            throw new RuntimeException("the orderId is illegal");
        }
        return (int) idBytes[3] == 1;
    }

    // parsing orderId and get order side
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
        return new BigDecimal(DexPrice.bytes2Double(price));
    }
}
