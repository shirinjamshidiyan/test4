package com.shirin.notificationservice.adapters.inbound.kafka;

import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

// inbound adapter infrastructure
/*
در این کد تو داری “موتور” Kafka listener را می‌سازی. اگر این را نسازی، Spring Kafka نمی‌داند:

چطور به Kafka وصل شود
پیام‌ها را چگونه deserialize کند
offset commit چه باشد
با خطاها چطور رفتار کند (retry/DLT)
listenerها با چه تنظیماتی اجرا شوند
 */
@Configuration
public class KafkaConsumerConfig {

  /*
      Container مسئول این است که:
  threadهای consumer را راه بیندازد
  poll کند
  deserialize کند
  listener method را call کند
  error handler را اعمال کند
  commit را مدیریت کند
  Factory یعنی: “قالب ساخت این containerها”.
       */

  // ساخت factory و consumer config (plumbing)

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEventV1>
      kafkaListenerContainerFactory(AppKafkaProps props, DefaultErrorHandler errorHandler) {
    // Deserializer: safe + deterministic for a single event type topic.
    //    JsonDeserializer<OrderConfirmedEventV1> deserializer = new
    // JsonDeserializer<>(OrderConfirmedEventV1.class);
    //    deserializer.addTrustedPackages("com.shirin.notificationservice.integration");

    //    deserializer.setRemoveTypeHeaders(true); // do not rely on producer headers
    //    deserializer.setUseTypeMapperForKey(false);

    Map<String, Object> cfg = new HashMap<>();
    cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers());
    cfg.put(ConsumerConfig.GROUP_ID_CONFIG, props.groupId());
    cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

    // Important: wrap real deserializer
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    // خود ErrorHandlingDeserializer به تنهایی پیام را DLT نمی‌فرستد.
    // ErrorHandlingDeserializer فقط کاری می‌کند که “خطای deserialize” از مرحله deserializer بیاید
    // بیرون و قابل مدیریت توسط ErrorHandler شود.

    // Real delegate deserializers
    cfg.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

    // Json specific config
    cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderConfirmedEventV1.class.getName());
    cfg.put(JsonDeserializer.TRUSTED_PACKAGES, OrderConfirmedEventV1.class.getPackage().getName());

    // Optional but useful for stability
    cfg.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000); // 5 min
    // مربوط به “گیر کردن processing thread” است
    // این مربوط به فاصله بین دو poll() است.
    cfg.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10_000); // heartbeat = 10 s
    // اگر طی این مدت heartbeat نرسد → coordinator فکر می‌کند consumer مرده
    // group rebalance می‌شود
    // Heartbeat توسط background thread مدیریت می‌شود.
    // حتی اگر record جدیدی نیاید، consumer heartbeat می‌فرستد.

    var consumerFactory = new DefaultKafkaConsumerFactory<String, OrderConfirmedEventV1>(cfg);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEventV1>();

    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    //        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // ConsumerFactory کاری می‌کند که:
    // با این cfg و deserializerها، یک KafkaConsumer واقعی بسازد.
    // Listener container از این consumer factory consumer می‌گیرد.

    return factory;
  }
}
