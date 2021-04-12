package io.undertree.demo.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I found this blog useful:
 * - https://blog.mimacom.com/testing-apache-kafka-with-spring-boot-junit5/
 */
@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class DemoKafkaProducerApplicationTests {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<String, CustomMessage> template;

    @Test
    void contextLoads() {
    }

    @Test
    void sendTestMessage() {
        template.send("test123", "123", CustomMessage.of("test-message", LocalDateTime.now()));
        Consumer<String, CustomMessage> consumer = configureCustomMessageConsumer("test123", "group1");

        ConsumerRecords<String, CustomMessage> records = KafkaTestUtils.getRecords(consumer);

        assertThat(records)
                .isNotNull()
                .hasSize(1)
                .extracting(ConsumerRecord::value)
                .extracting(CustomMessage::getMessage)
                .containsOnly("test-message");

        consumer.close();
    }

    /*
     * Create a synthetic consumer for the topic
     */
    private Consumer<String, CustomMessage> configureCustomMessageConsumer(String topic, String consumerGroup) {
        Consumer<String, CustomMessage> consumer =
                new DefaultKafkaConsumerFactory<>(
                        KafkaTestUtils.consumerProps(consumerGroup, "true", embeddedKafkaBroker),
                        new StringDeserializer(), jsonDeserializer())
                        .createConsumer();
        consumer.subscribe(Collections.singleton(topic));
        return consumer;
    }

    /*
     * Custom Deserializer with special Jackson "trust"
     */
    private JsonDeserializer<CustomMessage> jsonDeserializer() {
        final JsonDeserializer<CustomMessage> valueDeserializer = new JsonDeserializer<>(objectMapper);
        valueDeserializer.addTrustedPackages("*"); // this is a test case, we'll trust everything for now...
        return valueDeserializer;
    }
}