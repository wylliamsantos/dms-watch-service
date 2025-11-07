package br.com.dms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WatchFolderProperties.class)
public class WatcherConfiguration {
}
