package com.chainwatch.backend;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChainWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChainWatchApplication.class, args);
    }
}
