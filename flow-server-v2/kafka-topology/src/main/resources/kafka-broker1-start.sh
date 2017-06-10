#!/usr/bin/env bash

source ./env.sh

export LOG_DIR=$LOG_ROOT_DIR/kafka-broker-1

$KAFKA_INSTALL_DIR/bin/kafka-server-start.sh -daemon $CONF_DIR/kafka-broker-1.properties