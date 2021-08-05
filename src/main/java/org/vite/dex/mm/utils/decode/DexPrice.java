package org.vite.dex.mm.utils.decode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;

public class DexPrice {

    private static final Logger logger = LoggerFactory.getLogger(DexPrice.class);

    public static String bytesToString(byte[] price) {

        byte[] integerBytes = new byte[8];
        byte[] decimalBytes = new byte[8];


        System.arraycopy(price, 0, integerBytes, 3, 5);
        System.arraycopy(price, 5, decimalBytes, 3, 5);

        StringBuilder builder = new StringBuilder(new BigInteger(integerBytes).toString()).append(".");

        String decimalString = String.valueOf(new BigInteger(decimalBytes).longValue());

        for (int i = 0; i < 12 - decimalString.length(); i++) {
            builder.append("0");
        }
        return builder.append(decimalString).toString();
    }

    public static BigInteger bytesToBigInteger(byte[] bb) {
        return (bb == null || bb.length == 0) ? BigInteger.ZERO : new BigInteger(1, bb);
    }

    public static BigDecimal bytesToBigDecimal(byte[] bb) {
        BigInteger value = bytesToBigInteger(bb);
        String str = value.toString();
        BigDecimal _value = new BigDecimal(str);
        return _value;
    }

    public static double bytes2Double(byte[] arr) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) (arr[i] & 0xff)) << (8 * i);
        }
        return Double.longBitsToDouble(value);
    }

}
