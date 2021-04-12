# 10. Lab - Kafka Replicas

## Goal(s)

So far we haven't been able to take advantage of Kafka's ability to replicate Topic Partitions with a single Broker. Now
that we are running a 3-node cluster with Docker, we can explore the impact of defining Topics with a replication
factor.

## Update the Producer's Topics

Modify the Producer configuration to increase the number of Replicas and restart:

```java
    @Bean
public NewTopic topicExample(){
        return TopicBuilder.name("topic2")
        .partitions(3)
        .replicas(3)
        .build();
        }
```

After you restart, describe the topic with the CLI. Has anything changed?

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka1.test.local:9192 --describe --topic topic2
```

By default, Kafka will not apply the changes to replicas automatically. To cause Kafka to reassign the partitions'
replicas, we need to create this file, call it `increase-replication-factor.json` as an example:

```json
{
  "version": 1,
  "partitions": [
    {
      "topic": "topic2",
      "partition": 0,
      "replicas": [ 100, 200, 300 ]
    },
    {
      "topic": "topic2",
      "partition": 1,
      "replicas": [ 100, 200, 300]
    },
    {
      "topic": "topic2",
      "partition": 2,
      "replicas": [ 100, 200, 300 ]
    }
  ]
}
```

This file has the configuration need to tell Kafka to assign replicas for `topic2` on each of the Brokers (100, 200, and
300).  When you are finished modifying the file, execute the following command:

```shell
$ bin/kafka-reassign-partitions.sh --bootstrap-server kafka1.test.local:9192 --reassignment-json-file increase-replication-factor.json --execute
```

This will start the Kafka task of reassigning the partitions described in the json file (in this case, we aren't moving
the existing partition ,only adding additional replicas). When the operation is done, we can verify the configuration
was applied to the topic:

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka1.test.local:9192 --describe --topic topic2
```

You should see something like this in the output:

```text
Topic: topic2   PartitionCount: 3       ReplicationFactor: 3    Configs: segment.bytes=1073741824
        Topic: topic2   Partition: 0    Leader: 100     Replicas: 100,200,300   Isr: 100,200,300
        Topic: topic2   Partition: 1    Leader: 100     Replicas: 100,200,300   Isr: 100,200,300
        Topic: topic2   Partition: 2    Leader: 200     Replicas: 100,200,300   Isr: 200,300,100
```

Here you can see that each Partition exists on a Broker and that each Broker contains a replica. This is useful for a
highly available Kafka cluster as it will allow a node to fail and still be functional.  Generally adding more replicas
than Kafka Brokers is a bad idea.  For production consideration, prefer at least 3 replicas and 3+ Brokers.

A final note, when there are multiple replicas of a partition, Kafka will ensure that one (and only one) of the Brokers
are the leader.  Producers will always send data to the leader of the specific partition.  Over time, the Leader
may change (for example if a Broker goes off-line for maintenance).  The Kafka API does all the hard work to maintain
this information, so you don't have to.

NOTE: The ISR (in-sync replicas) information that we saw about is also important to be aware of.  If Kafka is having
issues keeping replicas in-sync that could indicate and underlying problem.

## The Producer's Responsibility (ACKs)

When we now think about the replication of partition data, it's important to consider what responsibility that the
Producer now plays to ensure consistency.

Kafka leaves it up to the Producer to ask for an acknowledgement (acks) based on how in-sync the replicas are (meaning
how many copies of the message data have been written).  Additionally, the Producer can decide if the acknowledgement
from Kafka is synchronous or asynchronous.

In our Spring Boot `application.yml` we can set the Producer's acks this way:

```yaml
spring:
  kafka:
    producer:
      acks: all  # 0, 1, or all
```

### acks=0
The Producer will not wait to receive an acknowledgement from the Leader (i.e. is asynchronous).  No guarantee can be
made about the consistency of the messages sent to the topic.

### acks=1 (default)
The Producer waits for the Leader to acknowledge, but does not wait for additional replicas.  If the Leader were to
fail, it is possible that the messages could be lost.

### acks=all
The Producer waits for the defined minimum number of replicas to acknowledge.  This configuration does not have any
impact unless the `min.insync.replicas` is set to something greater than 1.

If you have consistency requirements, change the value to "all" as demonstrated above.  However, the default
`min.insync.replicas` is 1.  Without changing this setting, the `all` ack setting is no different from the default of
`1`

## Setting `min.insync.replicas=2` on the Topic

Because we are using the Spring
[KafkaAdmin](https://github.com/spring-projects/spring-kafka/blob/master/spring-kafka/src/main/java/org/springframework/kafka/core/KafkaAdmin.java)
client to create the topics in the Producer, we can add an additional configuraton to the NewTopic @Bean:

```java
    @Bean
    public NewTopic topicExample() {
        return TopicBuilder.name("topic2")
                .partitions(3)
                .replicas(3)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(2))
                .build();
    }
```

This will ensure that when the Producer acks is `all`, 2 of the 3 replicas must have acknowledged.  This is usually a
minimum recommended setting for production data where consistency is essential.

Unfortunately, again just restarting the Producer does not change the topic's configuration.  For simplicity here, we'll
just delete the topic and restart (we don't really care about those old test messages).

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka3.test.local:9392 --delete --topic topic2
```

After you restart, describe the topic to verify the configuration change:

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka3.test.local:9392 --describe --topic topic2
```

The output should be similar to this:

```text
Topic: topic2	PartitionCount: 3	ReplicationFactor: 3	Configs: min.insync.replicas=2,segment.bytes=1073741824
	Topic: topic2	Partition: 0	Leader: 300	Replicas: 300,200,100	Isr: 300,200,100
	Topic: topic2	Partition: 1	Leader: 200	Replicas: 200,100,300	Isr: 200,100,300
	Topic: topic2	Partition: 2	Leader: 100	Replicas: 100,300,200	Isr: 100,300,200
```

Note that the "Configs:" now contains `min.insync.replicas=2`.

## Producer Retries

In addition to acknowledgement settings, the Producer can also be configured to retry the `send()`.  The default value is
`Integer.MAX_VALUE`.  To test this out, lets create a failure scenario that should conflict with our configurations.
Stop the kafka2 and kafka3 brokers:

```shell
$ docker-compose stop kafka2 kafka3
```

Confirm that the Brokers have stopped and restart the Producer.  You should see a large number of error messages like 
this:

```text
2021-01-18 16:25:55.335  WARN 26550 --- [ad | producer-1] o.a.k.clients.producer.internals.Sender  : [Producer clientId=producer-1] Got error produce response with correlation id 43 on topic-partition topic2-0, retrying (2147483606 attempts left). Error: NOT_ENOUGH_REPLICAS
```

You can stop the application to keep from filling up your logs.  Otherwise, if we restart just one of the stopped
Brokers, we should see the Producer recover:

```shell
$ docker-compose start kafka2
```

The errors should have stopped and you should be able to verify that 2 of the 3 replicas containe the messages that were
sent.

```shell
$ bin/kafka-topics.sh --bootstrap-server kafka1.test.local:9192 --describe --topic topic2
```

In the `Isr:` field you should see that 300 is not reporting.  To fix that, let's restart kafka3 and re-run the describe
command.

```shell
$ docker-compose start kafka3
```

Congratulations!, the cluster is healthy again.

## Producer Idempotence

Producer Idempotence is a newer feature of Apache Kafka and Spring Apache Kafka still has alpha/beta support for the
feature.  Effectively, idempotence can be used to ensure "exactly-once" semantics for Producers so that there is a
better guarantee that messages don't get resent during retries.  You can read about the Spring Boot implementation [here](https://docs.spring.io/spring-kafka/docs/current/reference/html/#exactly-once).

## Extra Credit

- A kafka topic has a replication factor of 3 and `min.insync.replicas` setting of 1.
  - What is the maximum number of brokers that can be down so that a Producer with `acks=all` can still produce to the
    topic?
 
- Experiment with stopping a Broker and watch the Consumers rebalance.

- Add an additional Broker (id 400) to the `docker-compose.yml` config and rebalance the `topic2` partitions across all
  4 of the nodes.  You should refer to this [documentation](https://cwiki.apache.org/confluence/display/KAFKA/Replication+tools#Replicationtools-4.ReassignPartitionsTool). 