#!/usr/bin/env bash

source ./env.sh

rm -rf $PROJ_DIR/data/*
rm -rf $PROJ_DIR/log/*

source ./zookeeper-local-start.sh
source ./kafka-broker1-start.sh
sleep 2

$KAFKA_INSTALL_DIR/bin/kafka-topics.sh --zookeeper localhost:2181 --create --topic process-in-topic --partitions 2 --replication-factor 1
$KAFKA_INSTALL_DIR/bin/kafka-topics.sh --zookeeper localhost:2181 --create --topic process-out-topic --partitions 2 --replication-factor 1
$KAFKA_INSTALL_DIR/bin/kafka-topics.sh --zookeeper localhost:2181 --create --topic command-topic --partitions 1 --replication-factor 1
