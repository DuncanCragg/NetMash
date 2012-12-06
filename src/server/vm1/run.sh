#!/bin/bash

javac -Xlint:unchecked -classpath ../../../build/netmash.jar *.java || exit

java -classpath .:../../../build/netmash.jar netmash.Cyrus > netmash.log 2>&1 &


