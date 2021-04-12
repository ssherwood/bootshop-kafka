# 10. Lab - Kafka Producer Tweaks

## Goal(s)

So far, we've used a lot of the default configurations for Kafka and Spring Boot. However, there are a few additional
performance related settings that can be added. This should be a shorter section.

## Compression Type

The Kafka API provides the ability to enable compression on messages prior to sending them to the Kafka Broker. This
reduces the amount of data being sent and processed and can improve performance and throughput. The default compression
type is none, so you have to manually specify it in the `application.yml`:

```yaml
spring:
  kafka:
    producer:
      compression-type: snappy
```

There are multiple compression types supported:

- none (default)
- gzip
- snappy
- lz4
- zstd

Generally speaking you may need to profile your specific use cases for which compression algorithm to use with `snappy`
being a good tradeoff between compression and CPU utilization. That said `zstd` is a newer option and shows a lot of
promise in certain use cases.

Note that you do not need to configure the Consumer about this change, Kafka will send the message metadata to the
consumer with any additional information it will need to decompress prior to parsing.

### Linger & Batch size

There are two additional settings that can improve throughput and performance of a Producer. The setting `linger.ms` is
set to 0 by default which means that as soon as a Producer thread is available, even if there is just one message, it'll
send the message. By increasing linger time, we encourage the Kafka Producer API to batch multiple messages together.

To enable batching, increase the linger.ms config in the `application.yml`:

```yaml
spring:
  kafka:
    producer:
      properties:
        "linger.ms": 100
```

This property is not defined in the Boot autoconfig properties (yet) but can be captured under "properties" as seen
here. When enabled, the Producer will all messages to be batched before sending with either via waiting for the maximum
of linger time or if the batch size is maxed out.

Finally, you can clamp down on the batch size to decide the maximum number of messages that can be batched within linger
time:

```yaml
spring:
  kafka:
    producer:
      batch-size: 10000
      properties:
        "linger.ms": 100
```

With any performance settings, it is imperative that real-world scenarios be run and measured against configuration
settings to validate the right sizes for these values.

## Extra Credit

- Observe the Kafka data logs using an editor that can handle binary data. What do you see in the log that stands out?
  Is there something unusual? Can you explain why?