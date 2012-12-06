#!/bin/bash

javac -Xlint:unchecked -classpath ../../../build/cyrus.jar *.java || exit

java -classpath .:../../../build/cyrus.jar cyrus.Cyrus > cyrus.log 2>&1 &


