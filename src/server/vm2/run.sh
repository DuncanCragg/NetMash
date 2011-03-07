#!/bin/bash

cp test-forest.db forest.db

javac -Xlint:unchecked -classpath ../../../build/netmash.jar *.java || exit

java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 &


