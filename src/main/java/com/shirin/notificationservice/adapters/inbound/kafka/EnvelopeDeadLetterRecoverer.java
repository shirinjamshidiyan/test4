package com.shirin.notificationservice.adapters.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.lang.Nullable;
import org.springframework.util.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EnvelopeDeadLetterRecoverer extends DeadLetterPublishingRecoverer {

    private static final String EHD_VALUE_EX_HEADER = "springDeserializerExceptionValue";
    private static final String EHD_KEY_EX_HEADER   = "springDeserializerExceptionKey";

    private final ObjectMapper om;
    public EnvelopeDeadLetterRecoverer(
            KafkaTemplate<String, DltEnvelope> template,
            ObjectMapper om) {
        super(template,
                (record, e) -> new TopicPartition(record.topic()+".DLT", record.partition()) );

        this.om=om;
    }

//این متد وقتی صدا زده می‌شود که سیستم می‌خواهد یک رکورد جدید برای topic مقصد (DLT) بسازد
        @Override
    protected ProducerRecord<Object, Object> createProducerRecord(
            ConsumerRecord<?, ?> record, //record : رکورد اصلی که از topic اصلی خوانده شد (topic اصلی، partition، offset، headers اصلی، key/value اصلی به شکل type generic)
            TopicPartition topicPartition, //topicPartition : مقصدی که recoverer تعیین کرده، مثلا order-confirmed.v1.DLT
            Headers headers, //headers : headerهایی که قرار است روی رکورد DLT قرار بگیرد (می‌تواند شامل headerهای اصلی و headerهای خطا باشد)
            @Nullable byte[] keyBytes, //key, value : “نمایش bytes” از key/value که recoverer فکر می‌کند باید publish کند.
            @Nullable byte[] valueBytes
            //byte[] keyBytes, byte[] valueBytes
            //نمایش byte-level از key/value که framework در این مرحله دارد.
            //نکته: در deserialization-fail روی این‌ها نمی‌شود کامل حساب کرد، چون raw ممکن است جای دیگری (headers) باشد.
    ) {

        // Extract useful strings from Spring-Kafka DLT headers (when listener threw)
        //اگر listener یا usecase exception داده، Spring Kafka این را در headers ذخیره می‌کند.
            // When listener throws, Spring Kafka adds string headers:
            // kafka_dlt-exception-fqcn, kafka_dlt-exception-message, kafka_dlt-exception-stacktrace
        String dltFqcn = extractStringHeader(headers, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String dltMsg = extractStringHeader(headers, KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        // Extract DeserializationException from ErrorHandlingDeserializer headers (when JSON deserialize failed)
        DeserializationException deserEx = extractDeserializationException(headers);


        // Decide stage using real exception if available, else use fqcn string
        String stage = determineStage(deserEx, dltFqcn, dltMsg);


        // Key
//            Key: bytes if available, else record.key()
        String originalKey = null;
        if (keyBytes != null) originalKey = safeUtf8(keyBytes);
        else if (record.key() != null) originalKey = String.valueOf(record.key());


        // Raw bytes: prefer raw from DeserializationException, else fall back to valueBytes
        // 4) Raw bytes: for deserialization failures, take from DeserializationException.getData()
        //    Otherwise fall back to valueBytes (best-effort)

            byte[] raw = null;
        if (deserEx != null && deserEx.getData() != null) raw = deserEx.getData();
        else if (valueBytes != null) raw = valueBytes;
        //اگر deserialize fail شده، raw را از deserEx.getData() می‌گیریم (مطمئن‌ترین)
        //اگر fail نشده ولی هنوز valueBytes داریم، برای completeness می‌گذاریم



        String rawText = raw != null ? safeUtf8(raw) : null;
        String rawBase64 = raw != null ? Base64.getEncoder().encodeToString(raw) : null;
        //چون raw ممکن است UTF-8 نباشد یا JSON خراب باشد. Base64 همیشه قابل ذخیره و replay است.


        // Typed JSON (only when we have a real object)
            // 5) eventJson: only if we truly have a typed object
        String eventJson = record.value() != null ? toJsonSafe(record.value()) : null;

        // Error type/message: prefer real exception class/message when deserEx exists
        String errorType = deserEx != null ? deserEx.getClass().getName() : dltFqcn;
        String errorMessage = deserEx != null ? deserEx.getMessage() : dltMsg;

//        String errorStacktrace = extractStringHeader(headers, KafkaHeaders.DLT_EXCEPTION_STACKTRACE);
//        if (errorStacktrace != null && errorStacktrace.length() > 4000) {
//            errorStacktrace = errorStacktrace.substring(0, 4000);
//        }

        DltEnvelope envelope = new DltEnvelope(
                record.topic(),
                record.partition(),
                record.offset(),
                originalKey,
                stage,
                errorType,
                errorMessage,
            //    errorStacktrace,
                rawText,
                rawBase64,
                eventJson
        );

        ProducerRecord<String, DltEnvelope> out = new ProducerRecord<>(
                topicPartition.topic(),
                topicPartition.partition(),
                null,
                null,
                envelope,
                headers
        );

            // Spring signature forces ProducerRecord<Object,Object>
        @SuppressWarnings({"rawtypes", "unchecked"})
        ProducerRecord<Object, Object> casted = (ProducerRecord) out;
        return casted;

    }

    private String determineStage(
            @Nullable DeserializationException deserEx,
            @Nullable String dltFqcn,
            @Nullable String dltMsg
    ) {
        if (deserEx != null) return "DESERIALIZATION";

        // fqcn-based
        if (dltFqcn != null) {
            if (dltFqcn.contains("DeserializationException")) return "DESERIALIZATION";
            if (dltFqcn.contains("InvalidEventException")) return "VALIDATION";
        }

        // message-based (your use case text)
        if (dltMsg != null) {
            if (dltMsg.contains("validation failed")) return "VALIDATION";
        }

        return "PROCESSING";
    }


    private DeserializationException extractDeserializationException(Headers headers) {

        // 1) direct known header names (most common, stable)
        DeserializationException ex = readDeserExFromHeader(headers, EHD_VALUE_EX_HEADER);
        if (ex != null) return ex;

        ex = readDeserExFromHeader(headers, EHD_KEY_EX_HEADER);
        if (ex != null) return ex;

        // 2) ultra-robust fallback: scan headers with that prefix
        // (useful if a shaded/relocated lib changes constant exposure but keeps names)
        // fallback scan
        for (Header h : headers) {
            if (h == null) continue;
            String k = h.key();
            if (k == null) continue;
            if (!k.startsWith("springDeserializerException")) continue;

            Object obj = safeDeserialize(h.value());
            if (obj instanceof DeserializationException de) return de;
        }

        return null;
    }

    private static DeserializationException readDeserExFromHeader(Headers headers, String headerKey) {
        Header h = headers.lastHeader(headerKey);
        if (h == null || h.value() == null) return null;

        Object obj = safeDeserialize(h.value());
        if (obj instanceof DeserializationException de) return de;
        return null;
    }

    private static Object safeDeserialize(byte[] bytes) {
        try {
            return SerializationUtils.deserialize(bytes);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String extractStringHeader(Headers headers, String key) {
        Header h = headers.lastHeader(key);
        if (h == null || h.value() == null) return null;
        return safeUtf8(h.value());
    }

    private String toJsonSafe(Object value) {
        try {
            return om.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String safeUtf8(byte[] bytes) {
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

}
