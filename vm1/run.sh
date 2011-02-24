#!/bin/bash

cp test-forest.db forest.db

javac -Xlint:unchecked -classpath ../build/jungle.jar *.java || exit

java -classpath .:../build/jungle.jar jungle.platform.Server > jungle.log 2>&1 &


