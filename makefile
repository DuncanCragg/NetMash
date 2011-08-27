################################################################################
#
# Where you want the release Android apk to be copied
#
RELEASE_TARGET=../net/netmash.net/NetMash.apk
#
################################################################################

noargs: editdb androidquick runquickserver

editdb:
	vi src/server/vm1/quick-forest.db

# -------------------------------------------------------------------

androidquick: clean init
	ant debug
	adb uninstall android.gui
	adb install bin/NetMash-debug.apk

androidquickrel: clean init
	ant release
	adb uninstall android.gui
	adb install bin/NetMash-release.apk

androidtest: clean init
	ant debug
	adb uninstall android.gui
	adb install bin/NetMash-debug.apk

androidtestrel: clean init
	ant release
	cp bin/NetMash-release.apk $(RELEASE_TARGET)

reinstall:
	adb uninstall android.gui
	adb install bin/NetMash-release.apk

testonphone: androidtestrel reinstall logcat

# -------------------------------------------------------------------

runquickserver: kill clean usequickdb run1 logout1

runremoteserver: kill clean setvmremoteconfig usetestdb run1 logout1

runcur: kill curconfig setvmtestconfig usetestdb run1n2

runall: kill allconfig setvmtestconfig usetestdb run1n2

runon1:
	( cd src/server/vm1 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

runon2:
	( cd src/server/vm2 ; java -classpath .:../../../build/netmash.jar netmash.NetMash > netmash.log 2>&1 & )

runtests: runjson runuid

runjson: jar
	java -ea -classpath ./build/netmash.jar netmash.lib.TestJSON

runuid: jar
	java -ea -classpath ./build/netmash.jar netmash.forest.UID

run1: jar
	(cd src/server/vm1; ./run.sh)

run2: jar
	(cd src/server/vm2; ./run.sh)

run1n2: run1 run2

# -------------------------------------------------------------------

usequickdb:
	cp src/server/vm1/quick-forest.db src/server/vm1/forest.db

usetestdb:
	cp src/server/vm1/test-forest.db src/server/vm1/forest.db
	cp src/server/vm2/test-forest.db src/server/vm2/forest.db

curconfig:
	cp src/server/vm2/curconfig.json src/server/vm2/netmashconfig.json

allconfig:
	cp src/server/vm2/allconfig.json src/server/vm2/netmashconfig.json

setvmtestconfig:
	sed -i"" -e "s:10.0.2.2:localhost:" src/server/vm1/netmashconfig.json

setvmremoteconfig:
	sed -i"" -e "s:10.0.2.2:netmash.net:" src/server/vm1/netmashconfig.json

setup:
	vim -o -N res/raw/netmashconfig.json res/raw/topdb.json src/server/vm1/netmashconfig.json src/server/vm1/test-forest.db src/server/vm2/curconfig.json src/server/vm2/allconfig.json src/server/vm2/test-forest.db

whappen:
	vim -o -N src/server/vm1/forest.db src/server/vm1/netmash.log src/server/vm2/forest.db src/server/vm2/netmash.log

logcat:
	adb logcat | tee ,logcat | egrep -vi "locapi|\<rpc\>"

logout1:
	tail -9999f src/server/vm1/netmash.log

logout2:
	tail -9999f src/server/vm2/netmash.log

# -------------------------------------------------------------------

classes: \
./build/classes/netmash/NetMash.class \


LIBOPTIONS= -Xlint:unchecked -classpath ./src -d ./build/classes

./build/classes/%.class: ./src/%.java
	javac $(LIBOPTIONS) $<

./build/classes:
	mkdir -p ./build/classes

jar: ./build/classes classes
	( cd ./build/classes; jar cfm ../netmash.jar ../META-INF/MANIFEST.MF . )

# -------------------------------------------------------------------

init:   proguard.cfg local.properties

proguard.cfg:
	android update project -p .

local.properties:
	android update project -p .

kill:
	-pkill java

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

# -------------------------------------------------------------------


