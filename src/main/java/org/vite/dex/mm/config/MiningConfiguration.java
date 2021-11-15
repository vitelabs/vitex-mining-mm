package org.vite.dex.mm.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class MiningConfiguration {
    @Value("${marketmining.nodeServerUrl}")
    private String nodeServerUrl;

    @Value("${marketmining.metaUrl}")
    private String metaUrl;

    @Value("${marketmining.saveStrategy}")
    private String saveStrategy;
}
