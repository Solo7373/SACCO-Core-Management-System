package com.sacco.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Rely on Spring Boot's default static resource serving from classpath:/static/
    // SPA routing is handled by SpaController
}
