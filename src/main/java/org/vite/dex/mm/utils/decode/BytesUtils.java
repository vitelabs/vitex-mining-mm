package org.vite.dex.mm.utils.decode;

import java.math.BigDecimal;
import java.math.BigInteger;

public class BytesUtils {

    private static BigInteger bytesToBigInteger(byte[] bb) {
        return (bb == null || bb.length == 0) ? BigInteger.ZERO : new BigInteger(1, bb);
    }

    public static BigDecimal quantityToBigDecimal(byte[] bb) {
        BigInteger value = bytesToBigInteger(bb);
        String str = value.toString();
        BigDecimal _value = new BigDecimal(str);
        return _value;
    }

    public static BigDecimal priceToBigDecimal(byte[] bb) {
        String value = priceToString(bb);
        return new BigDecimal(value);
    }

    private static String priceToString(byte[] price) {
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
}
