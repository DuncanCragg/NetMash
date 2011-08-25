
################################################################################

# Where you want the Android apk to be copied (not for quickstart)

DEBUG_TARGET=~/HostDesktop

# Where you want the release Android apk to be copied (not for quickstart)

RELEASE_TARGET=../net/netmash.net/NetMash.apk

################################################################################

noargs:
	@echo "make android && make editdb && make runlocalserver"

classes: \
./build/classes/netmash/Version.class \
./build/classes/netmash/NetMash.class \
./build/classes/netmash/platform/Kernel.class \
./build/classes/netmash/platform/Module.class \
./build/classes/netmash/platform/ChannelUser.class \
./build/classes/netmash/platform/FileUser.class \
./build/classes/netmash/lib/JSON.class \
./build/classes/netmash/lib/TestJSON.class \
./build/classes/netmash/lib/PathOvershot.class \
./build/classes/netmash/forest/UID.class \
./build/classes/netmash/forest/FunctionalObserver.class \
./build/classes/netmash/forest/WebObject.class \
./build/classes/netmash/forest/Persistence.class \
./build/classes/netmash/forest/HTTP.class \
./build/classes/netmash/forest/Notifiable.class \
./build/classes/server/types/Twitter.class \
./build/classes/server/types/UserHome.class \


LIBOPTIONS= -Xlint:unchecked -classpath ./src -d ./build/classes

./build/classes/%.class: ./src/%.java
	javac $(LIBOPTIONS) $<

init:   proguard.cfg local.properties

proguard.cfg:
	android update project -p .

local.properties:
	android update project -p .

run1: jar
	(cd src/server/vm1; ./run.sh)

run2: jar
	(cd src/server/vm2; ./run.sh)

curconfig:
	cp src/server/vm2/curconfig.json src/server/vm2/netmashconfig.json

allconfig:
	cp src/server/vm2/allconfig.json src/server/vm2/netmashconfig.json

editdb:
	vi src/server/vm1/test-forest.db

runlocalserver: kill clean setlocalconfig run1 logout1

runnet: kill clean setnetconfig run1 logout1

runcur: kill curconfig run1n2

runall: kill allconfig run1n2

run1n2: run1 run2

runon1:
	( cd src/server/vm1 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

runon2:
	( cd src/server/vm2 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

whappen:
	vim -o -N src/server/vm1/forest.db src/server/vm1/netmash.log src/server/vm2/forest.db src/server/vm2/netmash.log

setappconfig:
	sed -i"" -e "s:localhost:10.0.2.2:" res/raw/netmashconfig.json

setlocalconfig:
	sed -i"" -e "s:localhost:10.0.2.2:" src/server/vm1/netmashconfig.json

setnetconfig:
	sed -i"" -e "s:localhost:netmash.net:" src/server/vm1/netmashconfig.json

setup:
	vim -o -N res/raw/netmashconfig.json res/raw/topdb.json src/server/vm1/netmashconfig.json src/server/vm1/test-forest.db src/server/vm2/curconfig.json src/server/vm2/allconfig.json src/server/vm2/test-forest.db

kill:
	-pkill java

check: clean runtests

runtests: runjson runuid

runjson: jar
	java -ea -classpath ./build/netmash.jar netmash.lib.TestJSON

runuid: jar
	java -ea -classpath ./build/netmash.jar netmash.forest.UID

./build/classes:
	mkdir -p ./build/classes

jar: ./build/classes classes
	( cd ./build/classes; jar cfm ../netmash.jar ../META-INF/MANIFEST.MF . )

android: clean init setappconfig
	ant debug
	adb uninstall android.gui
	adb install bin/NetMash-debug.apk

androidrel: clean init setappconfig
	ant release
	adb uninstall android.gui
	adb install bin/NetMash-release.apk

netmashdebug: clean init
	ant debug
	cp bin/NetMash-debug.apk $(DEBUG_TARGET)

netmashtestrel: clean init
	ant release

netmashrel: clean init
	ant release
	cp bin/NetMash-release.apk $(RELEASE_TARGET)

reinstall:
	adb uninstall android.gui
	adb install bin/NetMash-release.apk

logcat:
	adb logcat | tee ,logcat | egrep -vi "locapi|\<rpc\>"

testonphone: netmashtestrel reinstall logcat

logout1:
	tail -9999f src/server/vm1/netmash.log

logout2:
	tail -9999f src/server/vm2/netmash.log

clean:
	rm -rf ./build/classes/netmash
	rm -rf ./build/classes/server
	rm -rf ./src/server/vm?/*.class
	rm -rf bin/classes bin/classes.dex
	rm -rf bin/NetMash.ap_ bin/NetMash*un*ed.apk
	rm -rf gen/android/gui/R.java

veryclean: clean
	rm -rf src/server/vm[12]/netmash.log
	rm -rf src/server/vm[12]/forest.db
	rm -rf bin/NetMash-*.apk
	git checkout res/raw/netmashconfig.json src/server/vm1/netmashconfig.json



