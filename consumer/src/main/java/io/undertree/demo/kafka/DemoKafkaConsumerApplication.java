package io.undertree.demo.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.util.backoff.FixedBackOff;

import java.time.LocalDateTime;

/**
 * A simple example Kafka Consumer...
 * <p>
 * https://www.confluent.io/blog/spring-kafka-can-your-kafka-consumers-handle-a-poison-pill/
 */
@Slf4j
@SpringBootApplication
public class DemoKafkaConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoKafkaConsumerApplication.class, args);
    }

    @KafkaListener(groupId = "demo-consumer", topics = "topic2", concurrency = "3")
    public void listen(ConsumerRecord<String, CustomMessage> message, @Headers MessageHeaders messageHeaders) {
        if ("FOO".equalsIgnoreCase(message.value().getMessage())) {
            throw new IllegalStateException("The message is invalid!");
        }

        log.info(String.format("Message from topic: '%s' received: (%s => %s) from offset: %d partition: %d with headers: %s",
                message.topic(), message.key(), message.value(), message.offset(), message.partition(), messageHeaders));
    }

    @Bean
    public NewTopic topicExample() {
        return TopicBuilder.name("topic2.DLT")
                .partitions(3)
                .replicas(3)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(2))
                .build();
    }

    /**
     * Allow for customization of default SeekToCurrentErrorHandler...
     *
     * @return
     */
    @Bean
    public SeekToCurrentErrorHandler customErrorHandler(KafkaOperations template) {
        return new SeekToCurrentErrorHandler(
                new DeadLetterPublishingRecoverer(template,
                        (rec, ex) -> {
                            log.error(String.format("An error occurred on topic '%s' with message: (%s => %s) at offset: %d on partition: %s",
                                    rec.topic(), rec.key(), rec.value(), rec.offset(), rec.partition()), ex);

                            // can do different topics based on the exception type too...
                            return new TopicPartition(rec.topic() + ".DLT", rec.partition());
                        }),
                new FixedBackOff(1500, 3));
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer customKafkaConsumerFactoryCustomizer() {
        return consumerFactory -> {
            // you can customize the ConsumerFactory here...
            log.info("Is Autocommit: " + consumerFactory.isAutoCommit());
        };
    }
}

@Data
@Jacksonized
@Builder
@AllArgsConstructor(staticName = "of")
class CustomMessage {
    private final String message;
    private final LocalDateTime timestamp;
}