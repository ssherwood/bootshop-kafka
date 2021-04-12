# 06. Lab - Spring Boot Testing

## Goal(s)

It is desirable to test our code in an automated way but unit testing is typically hard to do with integration of server
components like databases and message queues. This lab should help provide some techniques that can be used to test code
locally.

## Embedded Kafka

The Spring Initializer automatically added a dependency for you for Kafka Testing. Open the dependencies POM file (
assuming you are using Maven) and look for this:

```xml

<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
```

Testing something like Kafka in a local or even Continuous Integration process can be hard and this is where Spring is
stepping in to help. In the `@SpringBootTest` annotated test case, add this annotation:

```java
@SpringBootTest
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
```

This JUnit5 annotations will manage an embedded Kafka instance using the main Spring Boot config. This can be utilized
throughout the test cases in this test class.  For more information, see here: https://docs.spring.io/spring-kafka/reference/html/#testing

Now we can autowire a few useful classes:

```java
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<String, CustomMessage> template;
```

For most situations, you may not use the `EmbeddedKafkaBroker` directly, but it can be handy to set up scenarios beyond
the scope of this lab.

Run the Tests to confirm that everything is wiring correctly.

## Add a new Test Case

Now add a new test case with a producer send message:

```java
    @Test
    void sendTestMessage() {
        template.send("test123", "123", new CustomMessage("test-message", LocalDateTime.now()));
    }
```

Notice we are specifying a topic `test123` that is not defined anywhere - the Embedded Kafka is auto creating it for us.
This is an example where we _might_ want to use that behavior, but it's not required.  The Topic we created in the
Spring Application with the KafkaAdmin will be created in the transient broker.

Run the Test class again and review the logs.  It should look similar to the real Broker logs we've already seen before.

So far this isn't a very good test, there are no assertions.  In order to assert on anything we first need to somehow
create an "inspectable" consumer.  Add the following code to you test class:

```java
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
```

We've not written a true Consumer yet, so we'll gloss over some details, but it's important to note a few things.

A Consumer needs a key and value deserializer (like the Producer needs the serializers).  We echo that configuration here
knowing that we have to customize the JsonDeserializer with the ObjectMapper as well as a bit of Jackson
configuration that allows our CustomMessage to be deserialized (just in the context of the test case).

Now if we flesh out the original test, we can also be the receiver of the message being sent:

```java
    @Test
    void sendTestMessage() {
        template.send("test123", 123, new CustomMessage("test-message", LocalDateTime.now()));
        Consumer<String, CustomMessage> consumer = configureCustomMessageConsumer("test123", "group1");
        ConsumerRecords<String, CustomMessage> records = KafkaTestUtils.getRecords(consumer);

        // TODO assert something...
        
        consumer.close();
    }
```

## AssertThat

Finally, lets experiment with assertions.  Spring Boot adds additional testing libraries that provide more expressive
assertions like AssertJ.  With AssertJ we can chain assertions together and build more readable tests.  Add this code
block to replace the `//TODO` above:

```java
           assertThat(records)
                .isNotNull()
                .hasSize(1)
                .extracting(ConsumerRecord::value)
                .extracting(CustomMessage::getMessage)
                .containsOnly("test-message");
```

This tests the records result to confirm that they are not null, contain exactly 1 record and
when extracted from the value and message is a single string.  More advanced AssertJ constructs can be made but this is
just meant to give you a start at constructing more interesting tests.

You probably already are asking, "is this really a unit test?".

## Extra Credit

- Write more tests.
  

- Does the test case "run" get slower as you add more tests?
  - If so, what is the cause?