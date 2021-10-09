package org.vite.dex.mm.entity;

import lombok.Data;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.UnderscoreStr;

@Data
public class TradePair {
    String tradeTokenSymbol;

    String quoteTokenSymbol;

    String tradeTokenId;

    String quoteTokenId;

    // sell side effective interval of distance 
    Double sellEffectiveInterval;

    // buy side effective interval of distance 
    Double buyEffectiveInterval;

    // enable market-mining func for the trade pair
    boolean marketMiningOpen;

    // max ratio of buyAmount/sellAmount when market-mining
    private double buyAmountThanSellRatio;

    // max ratio of sellAmount/buyAmount when market-mining
    private double sellAmountThanBuyRatio;

    // the multiple factor of reward for the trade pair
    private double mmRewardMultiple;

    public String getTradePair() {
        return getTradeTokenId() + UnderscoreStr + getQuoteTokenId();
    }

    public int getMarket() {
        int market = 0;
        switch (quoteTokenSymbol) {
        case "VITE-000":
            market = 1;
            break;
        case "ETH-000":
            market = 2;
            break;
        case "BTC-000":
            market = 3;
            break;
        case "USDT-000":
            market = 4;
            break;
        }
        return market;
    }
}
