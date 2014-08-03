#!/bin/bash

/usr/lib/jvm/java-1.5.0-gcj-4.8-amd64/bin/javac  -Xlint:unchecked -classpath ../../../build/cyrus.jar *.java || exit

/usr/lib/jvm/java-1.5.0-gcj-4.8-amd64/bin/java -classpath .:../../../build/cyrus.jar cyrus.NetMash > cyrus.log 2>&1 &


