#!/usr/bin/env bash

KAFKA_HOME=$HOME/Repos/kafka_2.13-2.7.0/
cd "$KAFKA_HOME"

./bin/zookeeper-server-start.sh config/zookeeper.properties &>/dev/null &
sleep 5
./bin/kafka-server-start.sh config/server.properties &>/dev/null &

#tail -f ./logs/server.log