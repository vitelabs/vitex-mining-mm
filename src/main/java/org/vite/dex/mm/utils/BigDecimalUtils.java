package org.vite.dex.mm.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BigDecimalUtils {

    public static Map<Integer, String> tokenAccuracyMap = new ConcurrentHashMap<>(100);

    public static Optional<String> getTokenAccuracy(int tokenDecimals) {
        String tokenAccuracy = tokenAccuracyMap.get(tokenDecimals);
        if (tokenAccuracy != null) {
            return Optional.of(tokenAccuracy);
        }
        if (tokenDecimals != 0) {
            StringBuilder stringBuilder = new StringBuilder("0.");
            for (int i = 0; i < tokenDecimals - 1; i++) {
                stringBuilder.append("0");
            }
            stringBuilder.append("1");
            tokenAccuracyMap.put(tokenDecimals, stringBuilder.toString());
            return Optional.of(stringBuilder.toString());
        }
        return Optional.of("1");
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b).setScale(8, BigDecimal.ROUND_DOWN);
    }

    public static BigDecimal divide(BigDecimal f, BigDecimal t) {
        if (BigDecimalUtils.isZero(t)) {
            return new BigDecimal("0").setScale(8, BigDecimal.ROUND_HALF_UP);
        }
        return f.divide(t, 8, BigDecimal.ROUND_HALF_UP);
    }

    public static BigDecimal divide(BigDecimal f, BigDecimal t, Integer scale) {
        if (BigDecimalUtils.isZero(t)) {
            return new BigDecimal("0").setScale(scale, BigDecimal.ROUND_HALF_UP);
        }
        return f.divide(t, scale, BigDecimal.ROUND_HALF_UP);
    }

    public static BigDecimal formatSellPrice(Integer decimals, BigDecimal value) {
        return value.setScale(decimals, RoundingMode.UP);
    }

    public static BigDecimal formatPriceOrQuantity(Integer decimals, BigDecimal value) {
        BigDecimal tm = value.setScale(decimals, RoundingMode.DOWN);
        if (tm.compareTo(BigDecimal.ZERO) == 0) {
            tm = value.setScale(decimals, RoundingMode.UP);
        }
        return tm;
    }

    public static BigDecimal formatAmount(Integer decimals, BigDecimal price, BigDecimal quantity) {
        BigDecimal tm = price.multiply(quantity);
        if (tm.setScale(decimals, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) == 0) {
            return tm.setScale(decimals, RoundingMode.UP);
        }
        return tm.setScale(decimals, RoundingMode.DOWN);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b).setScale(8, BigDecimal.ROUND_DOWN);
    }

    public static BigDecimal priceFormat(String price) {
        return new BigDecimal(price).setScale(12, RoundingMode.DOWN);
    }

    public static BigDecimal feeFormat(String value, int tokenDecimals) {
        return quantityFormat(value, tokenDecimals);
    }


    public static BigDecimal percentAmount(String percent, BigDecimal value) {
        return value.multiply(new BigDecimal(percent));
    }

    public static BigDecimal amountFormat(String value, int tokenDecimals) {
        return quantityFormat(value, tokenDecimals);
    }

    private static final BigDecimal ZERO_BIG_DECIMAL = new BigDecimal(0);

    public static BigDecimal quantityFormat(String value, int tokenDecimals) {
        Optional<String> tokenAccuracy = getTokenAccuracy(tokenDecimals);
        if (tokenAccuracy.isPresent()) {
            return new BigDecimal(value).multiply(new BigDecimal(tokenAccuracy.get())).setScale(tokenDecimals, RoundingMode.DOWN);
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? b : a;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static boolean moreThan(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    public static boolean isZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }
}
