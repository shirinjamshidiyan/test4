package com.shirin.notificationservice.adapters.inbound.kafka;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// مدل تنظیمات (data فقط)
@Validated
@ConfigurationProperties(
    prefix = "app.kafka") // need to have @ConfigurationPropertiesScan on the main class
public record AppKafkaProps(
    @NotBlank String bootstrapServers, @NotBlank String groupId, @NotBlank String topic) {}
