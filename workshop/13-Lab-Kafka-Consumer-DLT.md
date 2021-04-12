# 13. Lab - Kafka Consumer Dead-Letter Topics

## Goal(s)

In this lab we'll discuss what happens when invalid messages get into the topic and what your Consumer can do to work
around them.

## "Poison Pills"

In messaging platforms we sometimes refer to messages that can't be processed due to format/parsing errors as "poison
pills". This is because once ingested by the consumer, they are often unable to continue processing (or end up
potentially losing large numbers of messages). Neither of these are desirable outcomes for a reslient platform.

The configurations that we added above allow used to intercept errors that might otherwise have sent the Consumer into a
retry loop. Restart the Consumer and send an invalid message now:

```shell
$ bin/kafka-console-producer.sh --bootstrap-server kafka1.test.local:9192 --topic topic2
>foo
```

This message does not conform to the POJO that we described and is missing critical information for Spring to handle it.
Because we have a custom `SeekToCurrentErrorHandler` error handler that just logs the event, we have failed to resolve
the message entirely. The poison pill is essentially thrown away - but what if we wanted to capture this message and
analyze/reprocess it later? Hint: the Spring
Kafka [SeekToCurrentErrorHandler](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/listener/SeekToCurrentErrorHandler.html)
has a feature that you can use with a customized "recoverer".

Again, from messaging platforms we'll introduce a "dead-letter" topic. A dead-letter queue/topic is just a place to put
messages that can't be processed for some reason, and we want to move them out of the processing stream (for later
inspection and re-runs).

Let's make a few changes to the Consumer's `SeekToCurrentErrorHandler`:

```java
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
```

Before we run the application again, we need to make a few more changes.  However, what we are telling Spring here is
that when we are handling errors, we want it to use a `DeadLetterPublishingRecoverer` which will route the message to a
different topic with ".DLT"

First, we've now just introduced that our Consumer is also now a Producer, so we need to configure those serializers in
the `application.yml`:

```yaml
server:
  port: 8081

spring:
  application:
    name: "demo-kafka-consumer"
  kafka:
    listener:
      ack-mode: batch # this is the default
      missing-topics-fatal: true
    consumer:
      # client-id: ${spring.application.name}
      # group-id: ${spring.application.name}-group
      auto-offset-reset: earliest
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.json.trusted.packages: "io.undertree.demo.kafka"
        spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.ByteArraySerializer
```

Since we don't know what format the data was sent for these kinds of error conditions, we'll use a simple
`ByteArraySerializer`.

Finally, we aren't relying on Topic auto-creation, so the Consumer needs to define the DLT topic:

```java
    @Bean
    public NewTopic topicExample() {
        return TopicBuilder.name("topic2.DLT")
                .partitions(3)
                .replicas(3)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(2))
                .build();
    }
```

In theory, each Consumer Group should be responsible for its own Dead-Letter Topic.

## Test it Out

Once you've made the changes above, restart the Consumer and the Producer.  You should be able to confirm that it is
handling the valid messages correctly, but now go ahead and submit and invalid message:

```shell
$ bin/kafka-console-producer.sh --bootstrap-server kafka1.test.local:9192 --topic topic2
```

- What happened?
- Can you determine if a message was saved in the DLT topic?

You can review more information and options [here](https://docs.spring.io/spring-kafka/reference/html/#dead-letters).