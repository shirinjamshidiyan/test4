package com.shirin.notificationservice.adapters.inbound.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shirin.notificationservice.core.application.usecase.SendReceiptEmailUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.lang.Nullable;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaErrorConfig {

    /*
    در Spring Kafka، اگر listener یک exception پرتاب کند:

Spring Kafka آن exception را به ErrorHandler می‌دهد.

ErrorHandler تصمیم می‌گیرد:

retry کند؟

چند بار retry کند؟

چقدر فاصله بین retryها باشد؟

اگر شکست خورد، پیام را کجا بفرستد؟

پس KafkaErrorConfig رفتار “failure path” را تعریف می‌کند.
     */

    //سیاست خطا (retry/DLT)

    @Bean
    public EnvelopeDeadLetterRecoverer deadLetterRecoverer(
            @Qualifier("dltEnvelopeKafkaTemplate") KafkaTemplate<String, DltEnvelope> dltKafkaTemplate,
            ObjectMapper om) {

        //        //DLQ publish: با DeadLetterPublishingRecoverer
//        //وقتی retry تمام شد و هنوز fail بود، این recoverer پیام را publish می‌کند به یک topic دیگر (DLT)
//        return new DeadLetterPublishingRecoverer(dltKafkaTemplate,
//                (record, ex) -> new TopicPartition(
//                        record.topic() + ".DLT", record.partition()));
        return new EnvelopeDeadLetterRecoverer(dltKafkaTemplate,om);

    }
// ErrorHandlingDeserializer فقط کاری می‌کند که “خطای deserialize” از مرحله deserializer بیاید بیرون و قابل مدیریت توسط ErrorHandler شود.
//    پیام کلن اشتباه باشد مستقیم برود DLT” با ترکیب این دو مشخص می‌شود:
//    ErrorHandlingDeserializer خطا را surface می‌کند
//    DefaultErrorHandler تصمیم می‌گیرد retry یا نه
//    Recoverer publish می‌کند به DLT

    @Bean
    public DefaultErrorHandler errorHandler(EnvelopeDeadLetterRecoverer  recoverer) {
        // backoff همون چیزی که خودت گذاشتی
      //  FixedBackOff backOff = new FixedBackOff(1000L, 120L); // مثال
        var backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxInterval(30_000L);
        backoff.setMaxElapsedTime(180_000L); // give enough time for retries // مثلا 3 دقیقه

        var handler=  new DefaultErrorHandler(recoverer, backoff);

        // اینها permanent هستند، retry نکن، مستقیم برو DLT
        //// Invalid payloads are permanent failures, do not retry
        ////        // Optional: do not retry on clearly permanent errors
        ////        //invalid = non-retryable + DLQ
        ////        // Permanent failures => no retry, go to DLT immediately
        // اینها “permanent” هستند، retry نکن، مستقیم برو DLT (با Envelope)
        handler.addNotRetryableExceptions(
                SendReceiptEmailUseCase.InvalidEventException.class,
                SendReceiptEmailUseCase.RetryExhaustedException.class,
                DeserializationException.class
        );
        return handler;


//اما مهم: خود deserializer به تنهایی DLT نمی‌فرستد.
//DLT شدن فقط وقتی رخ می‌دهد که:
//
//Exception به ErrorHandler برسد
//
//و ErrorHandler تصمیم بگیرد retry نکند
//
//و recoverer publish کند به DLT

//        ErrorHandlingDeserializer خودش “policy” ندارد که تصمیم بگیرد DLT یا retry.
//                کارش این است:
//
//        اگر deserialize موفق شد → همان value را می‌دهد
//
//        اگر fail شد →
//
//        exception را داخل headers می‌گذارد
//
//        و معمولاً value را null می‌کند یا یک DeserializationException wrapper تولید می‌کند (بسته به config)
//
//        پس اینکه “بعدش چه شود” را DefaultErrorHandler تعیین می‌کند.

    }

//    static class EnvelopeDltRecoverer extends DeadLetterPublishingRecoverer {
//
//        private final ObjectMapper om;
//
//        EnvelopeDltRecoverer(KafkaTemplate<String, String> template, ObjectMapper om) {
//            super(template, (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
//            this.om = om;
//        }

//        @Override
//        protected ProducerRecord<Object, Object> createProducerRecord(
//                ConsumerRecord<?, ?> record,
//                TopicPartition topicPartition,
//                Headers headers,
//                @Nullable byte[] keyBytes,
//                @Nullable byte[] valueBytes)
//        {
//
//
//            String keyStr = keyBytes == null ? null : new String(keyBytes, StandardCharsets.UTF_8);
//
//            String payloadStr;
//
//            if (valueBytes != null) {
//                payloadStr = new String(valueBytes, StandardCharsets.UTF_8);
//            } else if (record.value() != null) {
//                // اگر deserialize موفق بوده و object داری
//                payloadStr = toSafeString(record.value());
//            } else {
//                payloadStr = "null";
//            }
//
//            String payloadType = record.value() == null
//                    ? "null"
//                    : record.value().getClass().getName();
//
//            var env = new DltEnvelope(
//                    record.topic(),
//                    record.partition(),
//                    record.offset(),
//                    keyStr,
//                    payloadType,
//                    payloadStr,
//                    extractExceptionClass(headers),
//                    extractExceptionMessage(headers),
//                    Instant.now(),
//                    Map.of()
//            );
//            String envJson = toJsonSafe(env);
//
//            // NOTE: چون template ما String,String است، اینجا هم key/value را String می‌سازیم
//            return new ProducerRecord<>(
//                    topicPartition.topic(),
//                    topicPartition.partition(),
//                    keyStr,
//                    envJson
//            );
//
//        }
//        private String extractExceptionClass(Headers headers) {
//            var h = headers.lastHeader("kafka_dlt-exception-fqcn");
//            return h == null ? "unknown" : new String(h.value(), StandardCharsets.UTF_8);
//        }
//
//        private String extractExceptionMessage(Headers headers) {
//            var h = headers.lastHeader("kafka_dlt-exception-message");
//            return h == null ? "unknown" : new String(h.value(), StandardCharsets.UTF_8);
//        }
//        private String toJsonSafe(Object o) {
//            try {
//                return om.writeValueAsString(o);
//            } catch (JsonProcessingException e) {
//                return "{\"error\":\"failed to serialize DLT envelope\",\"message\":\"" + e.getMessage() + "\"}";
//            }
//        }
//        private String toSafeString(Object value) {
//            if (value == null) return "null";
//
//            if (value instanceof byte[] bytes) {
//                // اگر payload bytes بود، برای خوانایی می‌تونی utf-8 فرض کنی
//                // یا Base64 کنی. اینجا utf-8 گذاشتم.
//                return new String(bytes, StandardCharsets.UTF_8);
//            }
//
//            // اگر POJO بود، تلاش می‌کنیم JSON کنیم، اگر نشد toString
//            try {
//                return om.writeValueAsString(value);
//            } catch (Exception ignore) {
//                return value.toString();
//            }
//        }
//    }



    }
//{"eventId":"21111111-1111-1111-1111-111111111110","orderId":"32222222-2222-2222-2222-222222222220","occurredAt":"2026-02-23T08:30:00Z","version":1,"customerEmail":"test@example.com","customerName":"Test User","totalAmount":123.45,"currency":"USD","items":[{"sku":"SKU-1","name":"Item 1","quantity":1,"unitPrice":123.45,"lineTotal":123.45}],"billingAddress":"teh","shippingAddress":"isf"}