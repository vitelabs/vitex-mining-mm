package org.vite.dex.mm.reward.bean;

import lombok.Data;

@Data
public class MiningRewardCfg {
    int marketId;
    String tradePairSymbol;
    double effectiveDistance = 0.2;
}
