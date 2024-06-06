package com.power.MRPUSA.config;

import javax.annotation.PostConstruct;

import com.power.MRPUSA.service.PhysicalServerCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The class ScheduleConfig is for scheduling physical server data collection. It injects a physicalservercollector
 * and scheduled its refresh() method to run every minute using cron expression
 */


@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "carbon-tracker.scheduler.enable", havingValue = "true")
public class ScheduleConfig {
    @Autowired
    @Lazy
    private PhysicalServerCollector physicalServerCollector;

    public ScheduleConfig() {
    }

    @PostConstruct
    public void init() throws InterruptedException {
        log.info("ScheduleConfig initialized");
    }

    @Scheduled(cron = "0 1 0 * * ?")
    @Qualifier("taskScheduler")
    public void physicalServerCollectorScheduler() {
        physicalServerCollector.refresh();
    }

    @Bean("server-info-executor")
    ThreadPoolTaskExecutor serverInfoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(500);
        executor.setThreadNamePrefix("server-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean("metric-task-scheduler")
    ThreadPoolTaskScheduler powerMetricTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(500);
        scheduler.setThreadNamePrefix("metric-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler defaultTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("default-task-");
        scheduler.setPoolSize(10);
        return scheduler;
    }
}
