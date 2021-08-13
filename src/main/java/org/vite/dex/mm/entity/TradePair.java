package org.vite.dex.mm.entity;

import lombok.Data;

import static org.vite.dex.mm.constant.constants.MMConst.UnderscoreStr;

@Data
public class TradePair {
    //trade-pair symbol
    String symbol;

    String tradeTokenSymbol;

    String quoteTokenSymbol;

    String tradeTokenId;

    String quoteTokenId;

    // the effective interval of distance when market-mining
    Double mmEffectiveInterval;

    // enable market-mining func for the trade pair
    boolean isMarketMiningOpen;
    
    // max ratio of buyAmount/sellAmount when market-mining
    private double buyAmountThanSellRatio;

    // max ratio of sellAmount/buyAmount when market-mining
    private double sellAmountThanBuyRatio;

    // the multiple factor of reward for the trade pair
    private double mmRewardMultiple;

    // enable trading as mining function for the trade pair
    boolean isTradingMiningOpen;

    public String getTradePairSymbol() {
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
            case "USDT—000":
                market = 4;
                break;
        }
        return market;
    }
}
