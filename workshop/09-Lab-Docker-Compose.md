# 09. Lab - Using Docker Compose

## Goal(s)

So far we've just run Kafka locally in a single node cluster. While this is convenient for local development it doesn't
emulate our production environments all that well - specifically, multi-broker clusters. In this lab we will set up a
multi-node cluster using Docker.

## Shutdown All

First stop the existing Producer/Consumer applications, Kafka Broker and Zookeeper node. You can use the shell scripts
bundled with the Kafka distribution (if you are running them in the foreground just ^C).

```shell
$ bin/kafka-server-stop.sh
```

```shell
$ bin/zookeeper-server-stop.sh
```

## Using Docker Compose

Docker compose is a useful tool that can allow you to configure more complex deployments of server process like Kafka
more simply than by hand. If you don't already have the binary for `docker-compose`, please review
this [documentation](https://docs.docker.com/compose/install/).

Docker compose uses a yml manifest file to describe multiple server process and their networking configuration, this
later part is very important so that the Zookeeper node can communicate with each Kafka Broker on the Docker network.
For a local deployment like this we need a few set-up steps before everything will work as intended.

## Local DNS

We want to run 3 separate Kafka Brokers and to be able to access them easily, we'll use a .local domain so that they
will resolve via DNS.

First, we need to know what IP address the Docker host is using. On Linux, you can get this by invoking
`ip a show docker0`. The inet address shown is the address you will use.  On Mac, Docker Desktop now uses the
localhost (127.0.0.1).

Update your local `/etc/hosts` file and add the following lines (replacing the inet address with your own machine):

```text
127.0.0.1	kafka1.test.local
127.0.0.1	kafka2.test.local
127.0.0.1	kafka3.test.local
```

Essentially, all Brokers will report `kafka*.test.local` as their domain, and you will be able to use this to configure
your bootstrap servers list (this will allow sharing the docker-compose.yml file across different Linux and Mac users).

Finally, you need to set up a data directory for where the Kafka data for each Broker will live. In our local example,
this was being stored in the `/tmp` folder. You can use a similar approach but there is not guarantee that the tmp
folders will persist across reboots, so if you care about the data use a directory that you control. You'll expose this
directory to the docker-compose file via environment variable:

```shell
$ export KAFKA_DATA=<path/to/your/data>
```

## Download the Kafka Compose File

Download the file [here](../docker/docker-compose.yml)
and put it somewhere relative to your Producer/Consumer project(s).

## Review the docker-compose file

First review the files contents.

You can see configuration that specifies zookeeper and a number of kafka services. Two specific configuration options
are of interest:

```yaml
  - KAFKA_CFG_ADVERTISED_LISTENERS=CLIENT://:9093,EXTERNAL://kafka1.test.local:9192
```

and:

```yaml
volumes:
  - ${KAFKA_DATA}/kafka1:/bitnami/kafka
```

The default Broker port of `9092` won't work when multiple Brokers are running on the same host. This overrides that
default and gives each instance a unique port. You'll need to refer to these if you want to connect to them in the
bootstrap-servers list (something
like: `kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392`).

The `$KAFKA_DATA` configuration maps a persistent volume for each Broker service under its own name (as a folder).
If you don't have this environment variable already configured, do so now or else the next steps will fail.

UNIX ONLY >>> **SKIP THIS IF YOU ARE ON A MAC**

  Finally, before you run docker-compose, you'll want to set permissions on the KAFKA_DATA folder.  Since the Bitnami container
  does not run the kafka process as root (it uses uid 1001), you'll need to set ownership properly:
  
  ```shell
  $ mkdir -p $KAFKA_DATA/zookeeper $KAFKA_DATA/kafka1 $KAFKA_DATA/kafka2 $KAFKA_DATA/kafka3
  $ sudo chown -R 1001 $KAFKA_DATA
  ```
<<<

## Docker Compose "UP"

From the command line, now start up the processes using:

```shell
$ docker-compose up -d
```

You should see output like this (after several container image pulls):

```text
Creating network "docker_default" with the default driver
Creating docker_zookeeper_1 ... done
Creating docker_kafka3_1    ... done
Creating docker_kafka1_1    ... done
Creating docker_kafka2_1    ... done
```

To see the status of the running processes, type:

```shell
$ docker-compose ps
```

You should be able to verify the exposed ports for each process as well.

If you want to scan the logs for all the services, use the command:

```shell
$ docker-compose logs
```

To see only the logs for a single Broker (replace with the service name with the one you want):

```shell
$ docker logs docker_kafka1_1
```

## Test the Set-up

To confirm the solution is working, try using your existing Kafka distributions scripts but point them to the alternate
bootstrap services like thus:

```shell
$ bin/kafka-console-producer.sh --bootstrap-server kafka1.test.local:9192 --topic first
```

Enter a few messages and finish with `^D`.

You should now be able to review the messages:

```shell
$ bin/kafka-console-consumer.sh --bootstrap-server kafka1.test.local:9192 --topic first --from-beginning
```

Finally, review the data that should now be available in `${KAFKA_DATA}`. Notice that only one of the Brokers has the
topic `first`. Why is that?

## Update the Producer/Consumer Config

To use this Kafka cluster in the Producer/Consumer applications we've already written, we need to override the default
bootstrap servers configuration provided by Spring Boot. In each `application.yml` file, add the following:

```yaml
spring:
  kafka:
    bootstrap-servers: kafka1.test.local:9192, kafka2.test.local:9292, kafka3.test.local:9392
```

^ You usually want to supply more than one bootstrap-server (in case one is off-line for maintenance).

Verify that the Consumer is able to see the messages that the Producer created.

## Extra Credit

- You may have noticed that this new Kafka cluster is configured to allow auto topic creation. Remember how we
  changed `auto.create.topics.enable` that in Lab 5? Make the change in the `docker-compose.yml` file to support that
  configuration (hint: use `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE`).
  

- If you are using IntelliJ, experiment with running the docker-compose via the IDE (as a Service).  Explore each
  service (environment, logs, files, etc). 

  
- Think about this approach with `docker-compose`, do you like it better than running Kafka as a stand-alone process?
  Why?