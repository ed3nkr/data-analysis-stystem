package com.reviewsales.config;

import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String COLLECT_EXECUTOR = "collectExecutor";

    /** 리뷰 수집 작업용 스레드 풀 (collector 는 한 번에 한 건씩 처리하므로 크게 둘 필요 없음) */
    @Bean(name = COLLECT_EXECUTOR)
    public Executor collectExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("collect-");
        executor.initialize();
        return executor;
    }

    /** 주간 자동 수집은 app.collect.weekly-enabled=true 일 때만 켠다 (기본 비활성화). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "app.collect", name = "weekly-enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
