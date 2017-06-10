#!/usr/bin/env bash

source ./env.sh

export ZOO_LOG_DIR=$LOG_ROOT_DIR/zookeeper
export ZOO_LOG4J_PROP="INFO,ROLLINGFILE"

$ZOO_INSTALL_DIR/bin/zkServer.sh start $CONF_DIR/zookeeper.properties