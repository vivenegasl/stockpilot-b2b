package com.stockpilot.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.shared.filter.IdempotencyFilter;
import com.stockpilot.shared.filter.repository.IdempotencyRecordRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyConfig {

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IdempotencyFilter(repository, objectMapper));
        registration.addUrlPatterns("/api/v1/orders/*", "/api/v1/orders");
        registration.setOrder(50);
        return registration;
    }
}
