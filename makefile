
# Where you want the Android apk to be copied
DEBUG_TARGET=~/HostDesktop
RELEASE_TARGET=../net/netmash.net/NetMash.apk

classes: \
./build/classes/netmash/NetMash.class \
./build/classes/netmash/platform/Kernel.class \
./build/classes/netmash/platform/Module.class \
./build/classes/netmash/platform/ChannelUser.class \
./build/classes/netmash/platform/FileUser.class \
./build/classes/netmash/lib/JSON.class \
./build/classes/netmash/lib/TestJSON.class \
./build/classes/netmash/forest/UID.class \
./build/classes/netmash/forest/FunctionalObserver.class \
./build/classes/netmash/forest/WebObject.class \
./build/classes/netmash/forest/Persistence.class \
./build/classes/netmash/forest/HTTP.class \
./build/classes/server/types/Twitter.class\


LIBOPTIONS= -Xlint:unchecked -classpath ./src -d ./build/classes

./build/classes/%.class: ./src/%.java
	javac $(LIBOPTIONS) $<

runnet: kill clean sethost run1

sethost:
	sed -i "s:localhost:netmash.net:" src/server/vm1/netmashconfig.json

run1: jar
	(cd src/server/vm1; ./run.sh)

run2: jar
	(cd src/server/vm2; ./run.sh)

curconfig:
	cp src/server/vm2/curconfig.json src/server/vm2/netmashconfig.json

allconfig:
	cp src/server/vm2/allconfig.json src/server/vm2/netmashconfig.json

runcur: kill curconfig run1n2

runall: kill allconfig run1n2

run1n2: run1 run2

runon1:
	( cd src/server/vm1 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

runon2:
	( cd src/server/vm2 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

whappen:
	vim -o -N src/server/vm1/forest.db src/server/vm1/netmash.log src/server/vm2/forest.db src/server/vm2/netmash.log

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

jar: classes
	( cd ./build/classes; jar cfm ../netmash.jar ../META-INF/MANIFEST.MF . )

netmash: clean
	ant debug
	cp bin/NetMash-debug.apk $(DEBUG_TARGET)

netmashtestrel: clean
	ant release

netmashrel: clean
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

moreclean: clean
	rm -rf src/server/vm[12]/netmash.log
	rm -rf src/server/vm[12]/forest.db
