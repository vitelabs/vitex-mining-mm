package org.vite.dex.mm.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class MiningConfiguration {
    @Value("${mining.nodeServerUrl}")
    private String nodeServerUrl;

    @Value("${mining.tradePairSettingUrl}")
    private String tradePairSettingUrl;

    @Value("${mining.tradeContractAddress}")
    private String tradeContractAddress;

}
