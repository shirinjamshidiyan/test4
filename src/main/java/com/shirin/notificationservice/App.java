package com.shirin.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.shirin.notificationservice")
// @EnableConfigurationProperties(AppKafkaProps.class)
@ConfigurationPropertiesScan
public class App {
  public static void main(String[] args) {
    // adding a hook for pre-commit
    SpringApplication.run(App.class, args);
  }
}
