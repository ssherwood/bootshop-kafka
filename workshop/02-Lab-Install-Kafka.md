# 02. Install Apache Kafka

## Goal(s)

The purpose of this lab is to install and configure a local instance of Apache Kafka. This is easier than it sounds as
there are only a few base requirements to run Apache Kafka. We will install this on your development desktop/laptop
first to ensure that we will have a working instance and additional command line tools available throughout the labs
(and beyond).

## Download the Apache Kafka Distribution

In a browser, go to the main [Apache Kafka](https://kafka.apache.org/) website.

This is a good starting point for information, documentation and updates but also has a link to download the latest
about Kafka. It also contains a link to download the
latest [Apache Kafka distribution](https://kafka.apache.org/downloads). At this time, Kafka 2.7 is the most current -
select the binary download option (don't worry about the Scala bit, just select the 2.13 distribution). If in doubt, use
this [link](https://www.apache.org/dyn/closer.cgi?path=/kafka/2.7.0/kafka_2.13-2.7.0.tgz) and select the mirror of
choice.

NOTE: Kafka itself is written in Scala but only developers using Scala need to worry about the Scala version that Kafka
is compiled with.

## Untar the Distro

The distribution contains everything we need to run Kafka but a JVM. Kafka requires JRE8+ (assuming this is not a
problem with devs but if not, please try SDKMan).

```shell
$ tar zxvf kafka_2.13-2.7.0.tgz
```

You can move the `kafka_2.13-2.7.0` folder anywhere that is convenient to your workspace. From that root folder, you
should see a folder listing like this:

```shell
drwxr-xr-x 3 user user 4.0K Jan  5 10:58 bin
drwxr-xr-x 2 user user 4.0K Jan  5 12:27 config
drwxr-xr-x 2 user user 4.0K Jan  5 10:57 libs
-rw-r--r-- 1 user user  30K Dec 16 08:58 LICENSE
drwxrwxr-x 2 user user 4.0K Jan  6 11:04 logs
-rw-r--r-- 1 user user  337 Dec 16 08:58 NOTICE
drwxr-xr-x 2 user user 4.0K Dec 16 09:03 site-docs
```

## Launch the Kafka Processes

Kafka still requires a Zookeeper to manage the cluster's controller election, topic configuration and ACLs (more on
those later). To start a single-node Zookeeper, type:

```shell
$ bin/zookeeper-server-start.sh config/zookeeper.properties
```

Note: we are using the vanilla `zookeeper.properties` file, you won't need to change this for this workshop, but feel
free to review its contents. A typical Kafka deployment would use a 3, 5 or even 7 node Zookeeper Ensemble to ensure
high availability.

Finally, in a different console tab, launch a kafka broker:

```shell
$ bin/kafka-server-start.sh config/server.properties
```

## Kafka Logs

The process logs can be found in the `/logs` folder, you may need to review these at times, but the most significant
folder Kafka uses is where it stores the actual data for the events/streams. The default configuration for Linux/Mac is
in `/tmp/kafka-logs`.

## Extra Credit

Review the `server.properties` file.

- How would you increase/decrease the default log retention duration?


- How would you set the maximum log size?
    - Why might this be a bad idea?
    - Why might it be a good?
