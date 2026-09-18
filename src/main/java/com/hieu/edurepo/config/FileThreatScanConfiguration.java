package com.hieu.edurepo.config;

import com.hieu.edurepo.service.FileThreatScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FileThreatScanConfiguration {
    @Bean
    @ConditionalOnMissingBean(FileThreatScanner.class)
    FileThreatScanner noOpFileThreatScanner() {
        // Local/dev remains self-contained. Production can replace this bean with ClamAV or another scanner.
        return (file, originalName) -> true;
    }
}
