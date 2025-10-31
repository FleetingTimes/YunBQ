package com.yunbq.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器配置。
 * 作用：为日志写入等后台任务提供独立线程池，避免阻塞请求线程。
 * 说明：线程池参数可按需调整，当前配置兼顾开发与中小规模生产场景。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 日志写入专用线程池。
     * - corePoolSize：常驻核心线程数；
     * - maxPoolSize：线程池最大线程数；
     * - queueCapacity：队列容量，达到后由 maxPoolSize 扩容；
     * - keepAliveSeconds：非核心线程空闲存活时间；
     * - threadNamePrefix：线程名前缀，便于排查；
     */
    @Bean("logTaskExecutor")
    public Executor logTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("log-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}