#!/bin/bash

javac -Xlint:unchecked -classpath ../../../build/cyrus.jar *.java || exit

java -classpath .:../../../build/cyrus.jar cyrus.NetMash > cyrus.log 2>&1 &


