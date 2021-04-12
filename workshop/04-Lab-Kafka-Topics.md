# 04. Lab - Create Kafka Topics

## Goal(s)

To review the Kafka installation and apply information we learned during the Kafka Concepts section. This lab will
leverage Apache Kafka via the CLI first to ensure we can use the basic tools to understand how we can create topics,
publish to topics and consume information from topics.

## Create a Topic

A topic is a unique sequence of events that share the same message format (key and value). A Producer can be any system
that produces data in a format that Kafka can consume it. To create a topic via the command line, type:

```shell
$ bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic first-topic
```

With this basic information, we've created a topic that can now be produced to (the Kafka broker provides defaults for
the other configuration items like Partitions and Replicas).

Confirm that Kafka has created the topic and review the configuration:

```shell
$ bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic first-topic
```

The output should look like this:

```text
Topic: first-topic	PartitionCount: 1	ReplicationFactor: 1	Configs: segment.bytes=1073741824
	Topic: first-topic	Partition: 0	Leader: 0	Replicas: 0	Isr: 0
```

This information confirms that the topic has 1 Partition and a Replication Factor of 1. It also confirms that the Leader
Broker is `0` and that the only Partition resides on there.  `Isr` stands for In-Sync Replicas and is useful to know
when a particular topic has Replicas that aren't in sync.

Additional information can be gleaned by reviewing the `/tmp/kafka-logs` folder. A folder called `first-topic-0` should
have been created there. Kafka stores the messages for a topic in the folder in multiple files called Segments. Over
time, you should be able to observe Kafka creating new Segments and dropping older segments that age out. Kafka never
changes an entry to a segment, it always appends.

## Produce Events

Again, from the command line, we can send an event (or message) to the topic we created:

```shell
$ bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic first-topic
```

This will make the shell interactive, type a few messages and press `^C` to stop. Each newline issued a message to the
topic with an empty (null) key.

So, where did the messages go?

## Consume Events

Finally, we can consume the events we created:

```shell
$ bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic first-topic --from-beginning
```

The process will continue to poll for new messages until you hit `^C`. Notice that the last parameter we sent was
called `--from-beginning`. This explicitly tells Kafka that this consumer wants to get events from the very beginning of
the stream.  There are other options for what Offset to start consuming from, they will be discussed later.

## Extra Credit

Create a new topic with 3 partitions (using `--partitions=3`) and publish several messages to it. Review
the `/tmp/kafka-logs` folder for the partition and review the differences between `first-topic`.

- What is the benefit of having multiple partitions?


- Are each of the .log files non-zero bytes now?  If not why?
