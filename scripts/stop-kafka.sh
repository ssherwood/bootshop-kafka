#!/usr/bin/env bash

KAFKA_HOME=$HOME/Repos/kafka_2.13-2.7.0/
cd "$KAFKA_HOME"

./bin/kafka-server-stop.sh &>/dev/null &
sleep 10
./bin/zookeeper-server-stop.sh config/zookeeper.properties &>/dev/null &

# HELPERS
#
# * Get the PID of the Kafka process (you may have to kill it manually):
#   $ ps -ax | grep java | grep -i 'kafka\.Kafka' | awk '{print $1}'
#
#