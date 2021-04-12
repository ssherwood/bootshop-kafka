# 12. Lab - Kafka Consumer SerDes

## Goal(s)

In the original Consumer implementation we only handled the message as a String. Since we went through the process of
Serializing/Deserializing (SerDes), in this lab we will update the solution and address some of the problems that will
come up.

## Add a POJO

In the Consumer let's add a POJO implementation that we can serialize into:

```java

@Data
@Jacksonized
@Builder
@AllArgsConstructor(staticName = "of")
class CustomMessage {
    private final String message;
    private final LocalDateTime timestamp;
}
```

Note the `@Jacksonized` - this is a newer feature in Lombok that helps handle deserializing with Jackson into objects
with `final` variables. If we now update the KakfaListener with the alternate signature:

```java
    @KafkaListener(groupId = "demo-consumer", topics = "topic2", concurrency = "3")
public void listen(ConsumerRecord<String, CustomMessage> message,@Headers MessageHeaders messageHeaders){
        //...
        }
```

If you re-run the Consumer and the Producer now, the Consumer should fail to handle the messages now. You might see an
error in the logs like this:

```text
Caused by: java.lang.ClassCastException: class java.lang.String cannot be cast to class io.undertree.demo.kafka.CustomMessage (java.lang.String is in module java.base of loader 'bootstrap'; io.undertree.demo.kafka.CustomMessage is in unnamed module of loader 'app')
```

The default deserializer is for Strings, not Json. Like with the Producer, we need to tell Spring that we want to use a
Json deserializer. However, it is not a naive mapping in the `application.yml`:

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
      auto-offset-reset: earliest
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
    properties:
      spring.json.trusted.packages: "io.undertree.demo.kafka"
      spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
      spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

There is a bit to unpack here.

First, the key/value deserializer is not directly mapped to the String/Json deserializer. Instead, we are wrapping them
with
custom [ErrorHandlingDeserializer](https://docs.spring.io/spring-kafka/api/org/springframework/kafka/support/serializer/ErrorHandlingDeserializer.html)
from Spring. This will delegate to the `spring.deserializer.*` for the key/values assuming there are no errors in the
processing of the message by Spring.

We also have to configure the `spring.json.trusted.packages` to the correct package that your POJO object resides in.
The default ObjectMapper does not automatically trust deserializing into an object unless you provide the namespace that
you trust (this feature is to help reduce serialization/deserialization vulnerabilities that have occurred with
Jackson).

If you see an error like this:

```text
Caused by: java.lang.IllegalArgumentException: The class 'io.undertree.demo.kafka.CustomMessage' is not in the trusted packages: [java.util, java.lang]. If you believe this class is safe to deserialize, please provide its name. If the serialization is only done by a trusted source, you can also enable trust all (*).
```

Verify that the package name that you are using is in the `spring.json.trusted.packages`.

Otherwise, it should be consuming and parsing the message correctly.  In the next lab, we'll see what happens when it
doesn't.

## Extra Credit

- Is Json SerDes the best format for Kafka?
  - What other serialization formats are available?
  - What are the pros/cons of each.


- If the Json structure changes over time, how will you support these changes?