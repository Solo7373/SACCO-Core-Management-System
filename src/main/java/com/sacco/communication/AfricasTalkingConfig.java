package com.sacco.communication;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the Africa's Talking HTTP client with explicit connect/read timeouts.
 * A hung provider connection must never hold a request thread indefinitely.
 */
@Configuration
@EnableConfigurationProperties(AfricasTalkingProperties.class)
public class AfricasTalkingConfig {

    @Bean
    public RestClient africasTalkingRestClient(AfricasTalkingProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getHttp().getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getHttp().getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.resolvedBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
