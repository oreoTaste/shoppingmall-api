package com.tikitaka.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // (1) 핵심 설정: 동시에 실행할 Job의 최대 개수를 설정합니다. 
        // Job의 실행 시간을 고려하여 적절히 설정해야 시스템 부하를 방지할 수 있습니다.
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("batch-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}