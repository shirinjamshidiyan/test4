package com.shirin.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.shirin.notificationservice")
//@EnableConfigurationProperties(AppKafkaProps.class)
@ConfigurationPropertiesScan
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}