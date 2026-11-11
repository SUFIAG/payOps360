package com.payops.identity.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl public URL of the web console; links in e-mails point here
 */
@ConfigurationProperties(prefix = "payops.app")
public record AppProperties(String baseUrl) {
}
