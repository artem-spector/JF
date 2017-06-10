#!/usr/bin/env bash

source ./env.sh

source ./kafka-stop-all-brokers.sh
source ./zookeeper-local-stop.sh

rm -rf $PROJ_DIR/data/*
rm -rf $PROJ_DIR/log/*
