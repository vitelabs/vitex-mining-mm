package org.vite.dex.mm.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUtils {
    /**
     * get trade pair settings from file
     * 
     * @note the returned content is text/plain, convert to String before
     *       unserialize
     * @return
     * @throws IOException
     */
    public static List<TradePair> getMarketMiningTradePairs(String settingUrl) throws IOException {
        List<TradePair> res = new ArrayList<>();
        URL url = new URL(settingUrl);

        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        char[] buf = new char[512];
        int len = 0;
        StringBuffer contentBuffer = new StringBuffer();
        while ((len = reader.read(buf)) != -1) {
            contentBuffer.append(buf, 0, len);
        }

        JSONArray jsonArr = JSONObject.parseObject(contentBuffer.toString()).getJSONArray("tradepairs");
        if (jsonArr != null) {
            res = jsonArr.toJavaList(TradePair.class);
        }
        return res;
    }

    /**
     * get miningReward config of each tradePair market
     * 
     * @return
     * @throws IOException
     */
    public static Map<String, MiningRewardCfg> miningRewardCfgMap(String settingUrl) throws IOException {
        Map<String, MiningRewardCfg> tradePairCfgMap = new HashMap<>();
        List<TradePair> tradePairs = getMarketMiningTradePairs(settingUrl);

        tradePairs.stream().forEach(tp -> {
            String symbol = tp.getTradePair();
            MiningRewardCfg miningRewardCfg = MiningRewardCfg.fromTradePair(tp);
            tradePairCfgMap.put(symbol, miningRewardCfg);
        });
        return tradePairCfgMap;
    }

    // Get the timestamp at 12:30 pm every day
    public static Long getFixedSnapshotTime() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 30);
        c.set(Calendar.SECOND, 0);

        return c.getTime().getTime() / 1000; // seconds
    }

    // Get the timestamp at 12:00 every day
    public static Long getFixedEndTime() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        
        return c.getTime().getTime() / 1000; // seconds
    }

    /**
     * get daily total released VX by cycleKey
     * @param cycleKey
     * @return
     * @deprecated
     */
    public static BigDecimal getVxAmountByCycleKey(int cycleKey) {
        int firstPeriodId = 199;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal ascendRate = new BigDecimal("1.0180435");
        BigDecimal descendRate = new BigDecimal("0.99810276");
        BigDecimal preheatMinedAmtPerPeriod = new BigDecimal(10000);
        if (cycleKey <= firstPeriodId && cycleKey >= 111) {
            return preheatMinedAmtPerPeriod;
        } else if (cycleKey < 111) {
            return amount;
        }

        cycleKey = cycleKey - firstPeriodId;

        for (int i = 0; i <= cycleKey; i++) {
            if (i == 0) {
                amount = preheatMinedAmtPerPeriod;
            } else if (i < 90) {
                amount = amount.multiply(ascendRate).setScale(18, RoundingMode.DOWN);
            } else if (i == 90) {
                amount = preheatMinedAmtPerPeriod.multiply(new BigDecimal(5));
            } else {
                amount = amount.multiply(descendRate).setScale(18, RoundingMode.DOWN);
            }
        }
        return amount;
    }
}
