package io.undertree.demo.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple example Kafka Producer...
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class DemoKafkaProducerApplication {

    private final AtomicLong messageInterval = new AtomicLong(100L);
    private final KafkaTemplate<String, CustomMessage> kafkaTemplate;

    DemoKafkaProducerApplication(KafkaTemplate<String, CustomMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoKafkaProducerApplication.class, args);
    }

    /**
     * Initiates creation of a topic with the specified configurations.  If the topic already exists, it will validate
     * the existing configuration(s).
     * <p>
     * Note, increasing the number of partitions will likely change message partitioning - this can be undesirable and
     * care should be taken to ensure changes are not made accidentally.
     */
    @Bean
    public NewTopic topicExample() {
        return TopicBuilder.name("topic2")
                .partitions(3)
                .replicas(3)
                //.config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(1000 * 60 * 24))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(2))
                .build();
    }

    @Scheduled(initialDelay = 1000, fixedRate = 3000)
    public void sendMessage() {
        //log.info("Sending a message");
        long messageKey = messageInterval.incrementAndGet();
        kafkaTemplate.send("topic2", String.valueOf(messageKey),
                CustomMessage.of(String.format("Testing %d", messageKey),
                        LocalDateTime.now()));
    }
}

@Data
@AllArgsConstructor(staticName = "of")
class CustomMessage {
    private final String message;
    private final LocalDateTime timestamp;
}

/**
 * By default the Spring Kafka's autoconfig is not automatically aware of the spring-boot-starter-web* autoconfigured
 * ObjectMapper - this makes customizations for things like LocalDateTime harder to manage.  This customizer is
 * automatically called by the Kafka autoconfig where we manually supply the value-serializer instead of using the
 * vanilla property "spring.kafka.producer.value-serializer".
 * <p>
 * There may be a better way to do this... need to research more about Spring Kafka autoconfigs...
 * <p>
 * See https://github.com/spring-projects/spring-boot/blob/bd4c6e51fad099e005faf8cc6271602f34c5f223/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/kafka/KafkaAutoConfiguration.java#L94
 */
@Component
class ObjectMapperProducerFactoryCustomizer implements DefaultKafkaProducerFactoryCustomizer {
    private final ObjectMapper objectMapper;

    public ObjectMapperProducerFactoryCustomizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(DefaultKafkaProducerFactory<?, ?> producerFactory) {
        if (Objects.nonNull(producerFactory)) {
            producerFactory.setValueSerializer(new JsonSerializer<>(objectMapper));
        }
    }
}