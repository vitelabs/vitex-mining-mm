package org.vite.dex.mm.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.vite.dex.mm.constant.consist.MiningConst;
import org.vite.dex.mm.model.pojo.MiningRewardCfg;
import org.vite.dex.mm.model.pojo.TradePair;
import org.vite.dex.mm.model.pojo.TradePairSetting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommonUtils {
    /**
     * get index meta info
     * 
     * @note the returned content is text/plain, convert to String before
     *       unserialize
     * @return
     * @throws IOException
     */
    public static List<TradePairSetting> getTpSettingMeta(String metaUrl) throws IOException {
        List<TradePairSetting> res = new ArrayList<>();
        URL url = new URL(metaUrl);

        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        char[] buf = new char[512];
        int len = 0;
        StringBuffer contentBuffer = new StringBuffer();
        while ((len = reader.read(buf)) != -1) {
            contentBuffer.append(buf, 0, len);
        }

        JSONArray jsonArr = JSONObject.parseObject(contentBuffer.toString()).getJSONArray("tpSettings");
        if (jsonArr != null) {
            res = jsonArr.toJavaList(TradePairSetting.class);
        }
        return res;
    }

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

    public static List<TradePair> getMarketMiningTradePairs(String metaUrl, int cycleKey) throws IOException {
        List<TradePairSetting> metaInfos = getTpSettingMeta(metaUrl);
        metaInfos = metaInfos.stream().filter(t -> t.getFromCycleKey() <= cycleKey && t.getToCycleKey() >= cycleKey)
                .collect(Collectors.toList());
        String tpSettingFileName = MiningConst.tpSettingFilePrefix + metaInfos.get(0).getTpSettingFile();
        return getMarketMiningTradePairs(tpSettingFileName);
    }

    /**
     * get miningReward config of each tradePair market
     * 
     * @return
     * @throws IOException
     */
    public static Map<String, MiningRewardCfg> miningRewardCfgMap(String metaUrl, int cycleKey) throws IOException {
        Map<String, MiningRewardCfg> tradePairCfgMap = new HashMap<>();
        List<TradePair> tradePairs = getMarketMiningTradePairs(metaUrl, cycleKey);

        tradePairs.stream().forEach(tp -> {
            String symbol = tp.getTradePair();
            MiningRewardCfg miningRewardCfg = MiningRewardCfg.fromTradePair(tp);
            tradePairCfgMap.put(symbol, miningRewardCfg);
        });
        return tradePairCfgMap;
    }

    // Get the timestamp at 12:00 by cycleKey
    public static Long getTimestampByCyclekey(int cycleKey) {
        return MiningConst.genesisTimestamp + cycleKey * 24 * 60 * 60;
    }

    /**
     * get daily total released VX by cycleKey
     * 
     * @param cycleKey
     * @return
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
