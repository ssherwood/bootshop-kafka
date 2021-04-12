# 06. Lab - Spring Boot Consumer

## Goal(s)

To develop a Spring Boot application that acts as a Kafka Consumer for a given topic. Additionally, we'll demonstrate
the testability of a Kafka Consumer and discuss advanced scenarios.

## Create a Consumer Application

Using https://start.spring.io create a new Spring Boot application with the exact same dependencies as before.  Once the
zip file is downloaded and unpacked, import it into your favorite IDE.

Note: you will already have the Producer running and listening on port 8080.  For local development we will need to
configure the consumer process to listen on 8081.  In the application.properties (renamed to yml):

```yaml
server:
  port: 8081

# Local configs only
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

Launch the Consumer.  It should successfully be listening on 8081.  Visit http://localhost:8081/actuator and review the
info presented.

## Create a Basic Kafka Listener

Unlike the Producer, a Consumer needs to provide a little more information to Kafka before it is allowed to start
consuming messages.  Add this code to the main application class:

```java
    @KafkaListener(groupId = "demo-consumer", topics = "topic2")
    public void listen(ConsumerRecord<String, String> message, @Headers MessageHeaders messageHeaders) {
        log.info(String.format("Message received: %s = %s with headers: %s", message.key(), message.value(), messageHeaders));
    }
```

^ You will need to add the `@Slf4j` annotation above the @SpringBootApplication like this:

```java
@Slf4j
@SpringBootApplication
```

Run the Consumer client application.

If this is the very first time you run the Consumer with the given `groupId` you should see the client
waiting for new messages until the Producer is rerun.  This is because of a default configuration called 
[auto-offset-reset](https://kafka.apache.org/documentation/#consumerconfigs_auto.offset.reset).  This configuration
changes how the consumer receives messages the very first time (or if the current message offset is no longer valid).

If you would like to override this behavior and see all messages, add this configuration:

```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest
```
You will need to change the Consumer groupId if you want to see it take effect here since the initial run of the client
application caused the consumer group to persist the latest offset already.  Also, if you review the info you'll see we
are receiving the message as a Java String, not a fully realized POJO.

## Polling

TODO - Discuss the fact that the Kafka Consumer API actually performs a `poll()` behind the scenes.  Spring Kafka makes
this a little less transparent, but it's important to know.


## Consumer Groups

It is important to note that we were required to provide a `groupId` for the Consumer.  This allows Kafka to know what
unique Consumer is requesting information and each Consumer Group will have its own unique `Offset` that will be used to
determine what are the latest messages to be delivered.

If you re-run the Consumer, you'll notice that it will sit and wait for new messages and not start over from the
beginning.  This is Spring Kafka making sure that the offsets are being committed when the messages are consumed.
Understanding how this feature works will be important to ensuring you have production quality behavior within your
consumers.  More on that later.

## Scaling Consumer Groups

More than one Consumer from the same Consumer Group can read information from Kafka.  Kafka ensures that this available
partitions for a given topic are made available Consumers withing a Consumer Group.  You can see this even with 1 Consumer
when you review the start-up logs:

```shell
demo-consumer: partitions assigned: [topic2-2, topic2-1, topic2-0]
```

You can add addtional concurrent consumers to the existing Spring Boot application by modifying the `@KafkaListener`
annotation this way:

```java
@KafkaListener(groupId = "demo-consumer", topics = "topic2", concurrency = "3")
```

This creates 3 separate listener threads that will appear to Kafka as 3 different Consumers in the same Consumer Group.
Make the change and restart the Consumer application.  Review the logs and notice the different negotiations going on
for Partitions:

```shell
2021-01-16 13:28:15.843  INFO 116668 --- [ntainer#0-0-C-1] o.s.k.l.KafkaMessageListenerContainer    : demo-consumer: partitions assigned: [topic2-0]
2021-01-16 13:28:15.843  INFO 116668 --- [ntainer#0-1-C-1] o.s.k.l.KafkaMessageListenerContainer    : demo-consumer: partitions assigned: [topic2-1]
2021-01-16 13:28:15.843  INFO 116668 --- [ntainer#0-2-C-1] o.s.k.l.KafkaMessageListenerContainer    : demo-consumer: partitions assigned: [topic2-2]
```

Spring Apache Kafka logs out with different names for the Container threads (in this case: `0-C-1`, `1-C-1`, and `2-C-1`).
You can now experiment with sending messages and seeing the different Consumer thread consuming from each of its
assigned partitions.

It is more likely that you'll run multiple instances of the same Consumer to scale out horizontally, but this is a
simple way to experiment with the concept.

**NOTEs**

- You won't usually have more Consumers in a Consumer Group than you have Partitions (they will be idle).
  Making good decisions up front about how many Partitions to have may save you scalability issues later.  You  may have
  more Partitions than Brokers, but this doesn't improve reliability (we will discuss Replicas later).
  

- Adding more Brokers is an easy way to scale a Kafka Cluster but adding more Partitions may cause issues if you rely on
  messages with the same Key landing in the same partition (after the resize).
  

- Read this article: https://medium.com/@anyili0928/what-i-have-learned-from-kafka-partition-assignment-strategy-799fdf15d3ab
