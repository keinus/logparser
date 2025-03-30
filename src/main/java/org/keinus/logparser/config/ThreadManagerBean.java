package org.keinus.logparser.config;

import org.keinus.logparser.util.ThreadManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThreadManagerBean {
    @Bean
    ThreadManager threadManager() {
        return new ThreadManager();
    }
}
