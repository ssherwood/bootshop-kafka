# 05. Lab - Spring Boot Producer

## Goal(s)

In this lab we will transition our knowledge to using Spring Boot as the vehicle to produce messages to our Kafka
topics. This will give us a basic understanding of how Spring Boot will autoconfigure, how configurations can be
overridden, and how to produce messages with proper key and value structures.

## Start dot Spring dot IO

As Josh Long frequently mentions, http://start.spring.io is one of his favorite places on the internet. It's also a good
place to start this lab.

You can keep most of the defaults if you want to be sure to select:

- Spring for Apache Kafka
- Spring Boot Actuator
- Spring Reactive Web
- Lombok

NOTE: Do not pick `Spring for Apache Kafka Streams` at this time. We'll introduce that later. We want to start with a
basic example of using the base APIs.

Download the `.zip` unzip and import the project into your favorite IDE.

## Confirm the Application

A Spring Boot application is generally runnable automatically without additional modification. Attempt to run the
project and confirm that you can see information on http://localhost:8080/actuator (if not, review the logs - Boot is
usually pretty good at explaining the problem).

If you want to be able to see the complete actuator and health details, update the `/resources` folder
application.properties (rename to yml):

```yaml
# Local configs only - not for production!!!
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

## Produce Messages

To create the most basic of Producer, add the following code to the Spring Boot application class:

```java
    @Bean
    ApplicationRunner produce(KafkaTemplate<String, String> kafkaTemplate) {
        return event -> {
            for (int i = 0; i < 10; i++) {
                // send to partition1 without a key
                kafkaTemplate.send("topic1", String.format("Testing %d", i));
            }
        };
    }
```

Review the logs after restarting the application.  There will be more info explicitly about the connection to the Kafka
cluster.

## Side Note: Topic Auto-Creation

You may have wondered at this point how the topic `partition1` is actually working since we never actually created it.
This is a default behavior in Kafka called `auto.create.topics.enable`.  We may not want this to happen in production
code, so let's disable it now.  Add this to the `server.properties` file in the config folder and restart the broker:

```properties
auto.create.topics.enable=false
```

Change the topic in the topic target in the `kafkaTemplate.send()` method to `topic2` and restart the application.
There should be several repeating errors similar to this:

```text
2021-01-06 15:29:45.756  WARN 113037 --- [ad | producer-1] org.apache.kafka.clients.NetworkClient   : [Producer clientId=producer-1] Error while fetching metadata with correlation id 355 : {topic2=UNKNOWN_TOPIC_OR_PARTITION}
```

The Kafka Producer API will attempt to retry until the topic exists or just continue to fail.  This is likely more
desirable in a production setting to prevent accidental runaway topic creation.

## Spring's Kafka Admin

We can use Spring Boot to create the topics we want for us using Kafka Admin.  Add the following code to the Spring Boot
application:

```java
    @Bean
    public NewTopic topicExample() {
        return TopicBuilder.name("topic2")
                .partitions(3)
                .replicas(1)
                .build();
    }
```

This enables the Spring Boot application to create and configure topics essential to itself on start-up.  There are some
caveats however as it is possible to change configuration and increase the partition count which might impact how
message keys get distributed (i.e. if you are expecting messages of the same key to continue to go to the same
partition, this change may break that).

## Producer with a Partition Key 

So far we have only sent messages to Kafka with an empty Partition key.  In these cases, Kafka automatically balances
the messages across the available partitions.  This is not typically how Kafka messages are saved in Kafka.  Introducing
a key now will help influence how Kafka will store the messages.  By default, Kafka will hash the message key and
modulus the resulting value by the number of partitions (i.e. hash(key) % partitions).  This will ensure that messages
with the same key will end up in the same partition.

This feature is important to the ability for Kafka to scale as well as help preserve message ordering within the context
of a partition key.

Modify the Producer code as follows:

```java
    kafkaTemplate.send("topic2", String.valueOf(i), String.format("Testing %d", i));
```

We've simply added a synthetic partition key.  If we re-run the program, messages with the key "1" will now always end
up in the same partition.  This will become useful when we start consuming the messages.

## Produce with SerDes

When using Kafka, messages are typically more complex than Strings and need some form of serialization/deserialization.
Kafka supports the ability to register customer serdes implementations.  Spring Kafka provides a Json converter for
Kafka that can be used to convert POJO objects to Json.

First, let's create a simple POJO:

```java
@Data
@AllArgsConstructor
class CustomMessage {
    private final String message;
    private final LocalDateTime timestamp;
}
```

Change the main produce() method as well (we need to handle the new CustomMessage type):

```java
    @Bean
    ApplicationRunner produce(KafkaTemplate<String, CustomMessage> kafkaTemplate) {
        return event -> {
            for (int i = 0; i < 10; i++) {
                kafkaTemplate.send("topic2", String.valueOf(i),
                        new CustomMessage(String.format("Testing %d", i), LocalDateTime.now()));
            }
        };
    }
```

Finally, set the key and value serializer in the `application.yml`:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

## Verify Messages

Use the Kafka Consumer CLI to verify the messages you produced:

```shell
$ bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic topic2 --from-beginning
```

## Extra Credit #1

You may have noticed that the output of LocalDateTime in CustomMessage is a little _odd_ (e.g.
`"timestamp":[2021,1,7,12,52,17,626364000]`).

- Why is that?
- What can you do to change that behavior?
- Can you customize it to produce output like `"timestamp":"2021-01-07T12:48:00.227244"`

## Extra Credit #2

If you visited the `actuator/health` endpoint you may have expected to see a Kafka health check.  There isn't one
automatically provided by the current version of Spring Boot.

- Why?
- Can you think of what kinds of things you may want to monitor?
- What about metrics?

## Extra Credit #3

Make the Producer more dynamic and introduce a periodic scheduled task to send a new message on a fixed interval (hint:
look up @EnableScheduling).
