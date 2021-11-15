package org.vite.dex.mm.model.pojo;

import lombok.Data;

@Data
public class TradePairSetting {
    int fromCycleKey;

    int toCycleKey;

    String tpSettingFile;
}
