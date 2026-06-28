package com.agentoffice.lc4j.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * LangGraph4j 执行环境配置 + Agent 线程池。
 */
@Slf4j
@Configuration
public class GraphConfig {

    @Bean
    public ExecutorService agentExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                10, 50, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                Thread.ofVirtual().name("agent-worker-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("Agent executor initialized: core=10, max=50, queue=200");
        return executor;
    }
}
