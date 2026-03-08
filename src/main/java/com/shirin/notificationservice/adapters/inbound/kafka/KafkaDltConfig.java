package com.shirin.notificationservice.adapters.inbound.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaDltConfig {

  @Bean
  public ProducerFactory<String, DltEnvelope> dltEnvelopeProducerFactory(KafkaProperties props) {
    Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties(null));
    cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // مهم: type header اضافه نکند
    cfg.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

    return new DefaultKafkaProducerFactory<>(cfg);
  }

  @Bean(name = "dltEnvelopeKafkaTemplate")
  public KafkaTemplate<String, DltEnvelope> dltKafkaTemplate(
      ProducerFactory<String, DltEnvelope> dltEnvelopeProducerFactory) {
    return new KafkaTemplate<>(dltEnvelopeProducerFactory);
  }

  // در Spring Kafka:
  //
  // DefaultErrorHandler تصمیم می‌گیرد retry یا DLT
  //
  // DeadLetterPublishingRecoverer publish می‌کند

  // در Spring Kafka وقتی consumer یک پیام می‌گیرد، دو نوع failure داریم:/
  // 1- Deserialization fail
  // در این حالت معمولاً record.value() اصلا object نمی‌شود و null می‌ماند
  // در deserialization-fail، raw bytes معمولا داخل record.value() نیست.
  // Spring Kafka وقتی از ErrorHandlingDeserializer استفاده می‌کنی، exception و raw bytes را داخل
  // headers می‌گذارد با این نام‌ها
  // -------private static final String EHD_VALUE_EX_HEADER = "springDeserializerExceptionValue";
  // -------private static final String EHD_KEY_EX_HEADER   = "springDeserializerExceptionKey";
  // //چون در deserialization-fail:
  //    //record.value() معمولاً null است
  //    //valueBytes ممکن است null یا نامعتبر باشد
  //    //تنها جای مطمئن برای raw bytes همین header است
  //// گر JSON خراب باشد، ErrorHandlingDeserializer یک DeserializationException را serialize می‌کند
  // و داخل header می‌گذارد.
  // این exception شامل getData() است، یعنی raw bytes اصلی پیام.

  // 2- Business/processing fail بعد از deserialize موفق
  // عنی object ساخته شده ولی validation یا اجرای use case شکست خورده (مثلا InvalidEventException یا
  // SMTP مشکل دارد).

}
