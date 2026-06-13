package com.qc.enterprise.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "securityContextExecutor")
    public Executor securityContextExecutor() {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(5);
        delegate.setMaxPoolSize(10);
        delegate.setQueueCapacity(100);
        delegate.setThreadNamePrefix("QC-MultiThread-");
        delegate.initialize();

        // 🪄 THE MAGIC: This tells Spring to automatically copy the SecurityContext
        // (the JWT token info) from the main thread to all background threads!
        return new DelegatingSecurityContextExecutor(delegate);
    }
}