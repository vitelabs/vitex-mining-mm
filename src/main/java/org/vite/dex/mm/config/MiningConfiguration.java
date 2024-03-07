package org.vite.dex.mm.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class MiningConfiguration {
    @Value("${marketmining.nodeServerUrl}")
    private String nodeServerUrl;

    @Value("${marketmining.tradePairSettingUrl}")
    private String tradePairSettingUrl;

    @Value("${marketmining.lookupTimeIncrement:5}")
    private long lookupTimeIncrement;

    @Value("${marketmining.maxLookupNum:100}")
    private long maxLookupNum;
}
