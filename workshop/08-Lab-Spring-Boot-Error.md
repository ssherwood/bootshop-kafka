# 08. Lab - Spring Boot Error Handling

## Goal(s)

Accounting for Errors and Exceptions is usually one of the more challenging things you will need to know about using
Kafka and Spring Boot.  In this lab we will create artifical error scenarios and demonstrate techniques for handling
them.

## Errors & Exceptions

- What happens if there is something wrong with the message?

- What if some part of the handling of the Message throws an exception?

These are important concerns that we will need to address to ensure a healthy Consumer.

First, lets create an artificial scenario where the client throws and exception given a non-standard message "FOO":

```java
    @KafkaListener(groupId = "demo-consumer", topics = "topic2", concurrency = "3")
    public void listen(ConsumerRecord<String, String> message, @Headers MessageHeaders messageHeaders) {
        if ("FOO".equalsIgnoreCase(message.value())) {
            throw new IllegalStateException("The message is invalid!");
        }

        log.info(String.format("Message received: %s = %s with headers: %s", message.key(), message.value(), messageHeaders));
    }
```

Use the command line to produce a message "FOO".

Before you do, however, consider the implications: what do you expect will happen?

```shell
$ bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic topic2
```

^ Type in foo and hit enter.

...

...

...

You should see several stack traces in the console with Spring retrying multiple times and then finally giving up.  The
default behavior is using a class `SeekToCurrentErrorHandler` with 10 retries, and a default fixed "backoff" policy.
One of the final log messages should look something like this:

```shell
2021-01-16 14:03:00.844 ERROR 118643 --- [ntainer#0-0-C-1] o.s.k.l.SeekToCurrentErrorHandler        : Backoff FixedBackOff{interval=0, currentAttempts=10, maxAttempts=9} exhausted for ConsumerRecord(topic = topic2, partition = 0, leaderEpoch = 0, offset = 25, CreateTime = 1610823775297, serialized key size = -1, serialized value size = 3, headers = RecordHeaders(headers = [], isReadOnly = false), key = null, value = foo)
```

Was this what you expected to happen?

## Error Handling

Spring provides several ways to potentially handle these kinds of errors.  Let's look at a few types and consider the
pros and cons of each.

A simple approach would be to register a customized error handler with this code:

```java
    @Bean
    public SeekToCurrentErrorHandler customErrorHandler() {
        return new SeekToCurrentErrorHandler(
                (consumerRecord, e) -> {
                    // you might try to recover it here...
                    log.info(String.format("An error occurred on topic '%s' with message: (%s => %s) at offset: %d on partition: %s",
                            consumerRecord.topic(),
                            consumerRecord.key(),
                            consumerRecord.value(),
                            consumerRecord.offset(),
                            consumerRecord.partition()));
                },
                new FixedBackOff(1500, 3)
        );
    }
```

Restart the Consumer and try sending the `foo` message again.

Depending on the nature of the root cause of the exception, it is important to consider what the recovery behavior and
backoff behavior should be.  In this naive case above, we are just logging the message and retrying 3 times with a fixed
1.5-second delay.

Because the default ack-mode is `batch` and we just finished the recovery with logging, the message is lost from a
processing perspective.

In future labs, we will want to improve this error handling to actual help with offloading messages that cause errors.

## Extra Credit

- Review https://kafka.apache.org/protocol#protocol_error_codes
  - What are the types of errors that are retryable?