package com.shirin.notificationservice.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class NotificationMetrics {

    public static final String EVENT_ORDER_CONFIRMED_V1_TYPE = "OrderConfirmedEventV1";
    public static final String TEMPLATE_RECEIPT = "receipt";
    public static final String PROVIDER_SMTP = "smtp";

    // counters
    private final Map<String, Counter> eventsConsumed = new ConcurrentHashMap<>();
    private final Map<String, Counter> eventsProcessed = new ConcurrentHashMap<>();
    private final Map<String, Counter> emailSendTotal = new ConcurrentHashMap<>();

    // timers
    private final Map<String, Timer> emailSendDuration = new ConcurrentHashMap<>();
    private final Map<String, Timer> renderDuration = new ConcurrentHashMap<>();
    private final Map<String, Timer> eventProcessingDuration = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // -------- Events --------

    //Every time we receive a Kafka message
    public void incEventConsumedCounter(String topic, String type) {
        String safeTopic = safeTag ( topic);
        String safeType = safeTag ( type);

        String key = safeTopic + "|" + safeType;
        eventsConsumed.computeIfAbsent(key, k ->
                Counter.builder("notifications.events.consumed.total")
                        .tag("topic", safeTopic)
                        .tag("type", safeType)
                        .register(registry)
        ).increment();
    }

    // result: success | duplicate | invalid | failed | exhausted
    public void incEventProcessedCounter(String result) {
        String safeResult = safeTag(result);
        eventsProcessed.computeIfAbsent(safeResult, k ->
                Counter.builder("notifications.events.processed.total")
                        .tag("result", safeResult)
                        .register(registry)
        ).increment();
    }


    // -------- Email sending --------
    // result: success | failure
    public void incEmailSendCounter(String provider, String template, String result) {
        String p = safeTag(provider);
        String t = safeTag(template);
        String r = safeTag(result);
        String key = p + "|" + t + "|" + r;
        emailSendTotal.computeIfAbsent(key, k ->
                Counter.builder("notifications.email.send.total")
                        .tag("provider", p)
                        .tag("template", t)
                        .tag("result", r) // success|failure
                        .register(registry)
        ).increment();
    }

    // result: success | duplicate | invalid | failed | exhausted
    public Timer eventProcessingTimer(String result) {
        String safeResult = safeTag(result);
        return eventProcessingDuration.computeIfAbsent(result, k ->
                Timer.builder("notifications.event.processing.seconds")
                        .tag("result", safeResult)
                        .publishPercentileHistogram()
                        .register(registry)
        );
    }


    // -------- Template rendering --------
    public Timer renderTimer(String template) {
        String t = safeTag(template);
        return renderDuration.computeIfAbsent(t, k ->
                Timer.builder("notifications.template.render.seconds")
                        .tag("template", t)
                        .publishPercentileHistogram()
                        .register(registry)
        );
    }

    // provider/template/result is fine for portfolio.
    // In larger prod systems you often avoid "result" tag on timers.
    public Timer emailSendTimer(String provider, String template, String result) {
        String p = safeTag(provider);
        String t = safeTag(template);
        String r = safeTag(result);
        String key = p + "|" + t + "|" + r;
        return emailSendDuration.computeIfAbsent(key, k ->
                Timer.builder("notifications.email.send.seconds")
                        .tag("provider", p)
                        .tag("template", t)
                        .tag("result", r)
                        .publishPercentileHistogram()
                        .register(registry)
        );
    }

    private static String safeTag(String s) {
        if (s == null || s.isBlank()) return "unknown";
        // defensive: avoid very long tag values
        String trimmed = s.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

}
