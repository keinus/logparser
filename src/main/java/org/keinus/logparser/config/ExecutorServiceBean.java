package org.keinus.logparser.config;

import java.util.concurrent.ExecutorService;

import org.keinus.logparser.util.CustomExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ExecutorServiceBean {
    @Bean
    ExecutorService executorService(Environment environment) {
        String threadName = environment.getProperty("spring.application.name", "threads");
        return new CustomExecutorService(threadName);
    }
}
