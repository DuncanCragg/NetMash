################################################################################
#
# Where you want the release Android apk to be copied
#
RELEASE_TARGET=../net/the-cyrus.net/Cyrus.apk
LOCAL_IP=192.168.0.6
#
################################################################################

examples: runstt
	chromium-browser 'http://localhost:8083/#http://localhost:8081/o/uid-f25a-08e1-7d7d-09f8.json'

tutorial: runtut
	chromium-browser 'http://localhost:8083/#http://localhost:8081/o/uid-7081-c95e-1c04-d7a5.json'

cars: runcars
	chromium-browser 'http://localhost:8083/#http://localhost:8082/o/uid-459e-4ba6-0b4b-5786.json'

####################

emu: androidemu runemu logcat

lan: androidlan runlan lancat

cap: androidemu runcap logcat

####################

tests: json uid cyrus

cyrus: runcyrus showtestresults

####################

all: androidemu runall logcat

loc: androidemu        logcat

rem: androidrem

lap: androidlan runlap lancat

sta: androidemu runsta logcat

# -------------------------------------------------------------------

demo: editstaticdb androidemu runquickserver logboth editdynamicfile

quickdyn: editquickdb androidemu runquickserver logboth editdynamicfile

# -------------------------------------------------------------------

httplog1:
	tail -999f src/server/vm1/cyrus.log | egrep '\------|Cache-Notify:|If-None-Match:|ETag:|Content-Type:|Content-Location:|^ +|^{ |^}|HTTP.1.1'

httplog2:
	tail -999f src/server/vm2/cyrus.log | egrep '\------|Cache-Notify:|If-None-Match:|ETag:|Content-Type:|Content-Location:|^ +|^{ |^}|HTTP.1.1'

# -------------------------------------------------------------------

setlanip: veryclean
	vi makefile

editstaticdb:
	vi -o -N src/server/vm1/static.db

editquickdb:
	vi -o -N src/server/vm1/quick.db

editlocaldb:
	vi -o -N src/server/vm1/local.db

editdynamicfile:
	vi -o -N src/server/vm1/functional-hyper.db src/server/vm1/functional-hyperule.db

editlocaldbanddynamicfile:
	vi -o -N src/server/vm1/local.db src/server/vm1/guitest.db

# -------------------------------------------------------------------

androidemu: clean init setappemuconfig setemumapkey
	ant debug
	adb -e uninstall cyrus.gui
	adb -e install bin/Cyrus-debug.apk

androidlan: clean init setapplanconfig setremmapkey
	ant release
	( adb -d uninstall cyrus.gui && adb -d install bin/Cyrus-release.apk ) &
	cp bin/Cyrus-release.apk $(RELEASE_TARGET)

androidrem: clean init setappremconfig setremmapkey
	ant release
	cp bin/Cyrus-release.apk $(RELEASE_TARGET)

installemu:
	adb -e install bin/Cyrus-debug.apk

installlan:
	adb -d install bin/Cyrus-release.apk

uninstallemu:
	adb -e uninstall cyrus.gui

uninstalllan:
	adb -d uninstall cyrus.gui

reinstallemu: uninstallemu installemu

reinstalllan: uninstalllan installlan

# -------------------------------------------------------------------

runall: kill clean netconfig usealldbs setvm3emuconfig run1n2

runemu: kill clean netconfig useworlddb setvm3emuconfig run1n2

runlan: kill clean netconfig useworlddb setvm3lanconfig run1n2

runrem: kill clean netconfig useworlddb setvm3remconfig run1n2

runcyrus: kill cyrusconfig   usecyrusdb setvm2tstconfig run2

runcars: kill clean netconfig usecarsdb setvm3tstconfig run1n2

runcap: kill clean netconfig useworlddb usecapdb setvm3emuconfig run1n2

runlap: kill clean netconfig usecapdb  setvm2lanconfig run2

runtut: kill clean           usetutordb setvm1tstconfig run1

runstt: kill clean           usestaticdb setvm1tstconfig run1

runsta: kill clean netconfig usestaticdb setvm1emuconfig run1

runcur: kill clean curconfig usetestdb setvm3tstconfig run1n2

runtst: kill clean tstconfig usetestdb setvm3tstconfig run1n2

runone: kill clean           usetestdb setvm1tstconfig run1

runtwo: kill clean curconfig usetestdb setvm3emuconfig run1n2

# -------------------------------------------------------------------

runode:
	( cd src/js/ ; node js/server.js > cyrus.log 2>&1 & )

# -------------------------------------------------------------------

runon1:
	( cd src/server/vm1 ; java -classpath .:../../../build/cyrus.jar cyrus.Cyrus > cyrus.log 2>&1 & )

runon2:
	( cd src/server/vm2 ; java -classpath .:../../../build/cyrus.jar cyrus.Cyrus > cyrus.log 2>&1 & )

json: jar
	java -ea -classpath ./build/cyrus.jar cyrus.lib.TestJSON

uid: jar
	java -ea -classpath ./build/cyrus.jar cyrus.forest.UID

run1: jar
	(cd src/server/vm1; ./run.sh)

run2: jar
	(cd src/server/vm2; ./run.sh)

run1n2: run1 run2

# -------------------------------------------------------------------

usealldbs: useworlddb
	cat src/server/vm1/static.db >> src/server/vm1/cyrus.db
	cat src/server/vm2/cap.db    >> src/server/vm2/cyrus.db

useworlddb:
	cp src/server/vm1/world.db src/server/vm1/cyrus.db
	cp src/server/vm2/world.db src/server/vm2/cyrus.db

usecyrusdb:
	cp src/server/vm2/om.db src/server/vm2/cyrus.db

usecapdb:
	cp src/server/vm2/cap.db src/server/vm2/cyrus.db

usecarsdb:
	cp src/server/vm1/cars.db src/server/vm1/cyrus.db
	cp src/server/vm2/cars.db src/server/vm2/cyrus.db

usetestdb:
	cp src/server/vm1/test.db src/server/vm1/cyrus.db
	cp src/server/vm2/test.db src/server/vm2/cyrus.db

usetutordb:
	cp src/server/vm1/tutorial.db src/server/vm1/cyrus.db

usestaticdb:
	cp src/server/vm1/static.db src/server/vm1/cyrus.db

setremmapkey:
	sed -i"" -e "s:03Hoq1TEN3zaDOQmSJNHwHM5fRQ3dajOdQYZGbw:03Hoq1TEN3zbEGUSHYbrBqYgXhph-qRQ7g8s3UA:" src/android/cyrus/gui/Cyrus.java

setemumapkey:
	sed -i"" -e "s:03Hoq1TEN3zbEGUSHYbrBqYgXhph-qRQ7g8s3UA:03Hoq1TEN3zaDOQmSJNHwHM5fRQ3dajOdQYZGbw:" src/android/cyrus/gui/Cyrus.java

setappemuconfig:
	sed -i"" -e "s:the-cyrus.net:10.0.2.2:g" res/raw/cyrusconfig.db
	sed -i"" -e "s:the-cyrus.net:10.0.2.2:g" res/raw/top.db
	sed -i"" -e "s:the-cyrus.net:10.0.2.2:g" src/android/cyrus/User.java
	sed -i"" -e   "s:$(LOCAL_IP):10.0.2.2:g" res/raw/cyrusconfig.db
	sed -i"" -e   "s:$(LOCAL_IP):10.0.2.2:g" res/raw/top.db
	sed -i"" -e   "s:$(LOCAL_IP):10.0.2.2:g" src/android/cyrus/User.java

setapplanconfig:
	sed -i"" -e "s:the-cyrus.net:$(LOCAL_IP):g" res/raw/cyrusconfig.db
	sed -i"" -e "s:the-cyrus.net:$(LOCAL_IP):g" res/raw/top.db
	sed -i"" -e "s:the-cyrus.net:$(LOCAL_IP):g" src/android/cyrus/User.java
	sed -i"" -e      "s:10.0.2.2:$(LOCAL_IP):g" res/raw/cyrusconfig.db
	sed -i"" -e      "s:10.0.2.2:$(LOCAL_IP):g" res/raw/top.db
	sed -i"" -e      "s:10.0.2.2:$(LOCAL_IP):g" src/android/cyrus/User.java

setappremconfig:
	sed -i"" -e    "s:10.0.2.2:the-cyrus.net:g" res/raw/cyrusconfig.db
	sed -i"" -e    "s:10.0.2.2:the-cyrus.net:g" res/raw/top.db
	sed -i"" -e    "s:10.0.2.2:the-cyrus.net:g" src/android/cyrus/User.java
	sed -i"" -e "s:$(LOCAL_IP):the-cyrus.net:g" res/raw/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):the-cyrus.net:g" res/raw/top.db
	sed -i"" -e "s:$(LOCAL_IP):the-cyrus.net:g" src/android/cyrus/User.java

setvm1emuconfig:
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm1/cyrus.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm1/cyrus.db

setvm2emuconfig:
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm2/cyrus.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm2/cyrus.db

setvm3emuconfig: setvm1emuconfig setvm2emuconfig

setvm3cleanconfig:
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e   "s:localhost:10.0.2.2:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):10.0.2.2:g" src/server/vm2/cyrusconfig.db

setvm2lanconfig:
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm2/cyrusconfig.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm2/cyrus.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm2/cyrus.db

setvm3lanconfig:
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm1/cyrusconfig.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm1/cyrusconfig.db
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm1/cyrus.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm1/cyrus.db
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm2/cyrusconfig.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:localhost:$(LOCAL_IP):g" src/server/vm2/cyrus.db
	sed -i"" -e  "s:10.0.2.2:$(LOCAL_IP):g" src/server/vm2/cyrus.db

setvm2tstconfig:
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm2/cyrus.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm2/cyrus.db

setvm3tstconfig:
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm1/cyrus.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm1/cyrus.db
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm2/cyrus.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm2/cyrus.db

setvm3remconfig:
	sed -i"" -e  "s:10.0.2.2:the-cyrus.net:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e  "s:10.0.2.2:the-cyrus.net:g" src/server/vm1/cyrus.db
	sed -i"" -e  "s:10.0.2.2:the-cyrus.net:g" src/server/vm2/cyrusconfig.db
	sed -i"" -e  "s:10.0.2.2:the-cyrus.net:g" src/server/vm2/cyrus.db

setvm1tstconfig:
	sed -i"" -e    "s:10.0.2.2:localhost:g" src/server/vm1/cyrusconfig.db
	sed -i"" -e "s:$(LOCAL_IP):localhost:g" src/server/vm1/cyrusconfig.db

netconfig:
	cp src/server/vm2/netconfig.db src/server/vm2/cyrusconfig.db

cyrusconfig:
	cp src/server/vm2/omconfig.db src/server/vm2/cyrusconfig.db

curconfig:
	cp src/server/vm2/curconfig.db src/server/vm2/cyrusconfig.db

tstconfig:
	cp src/server/vm2/allconfig.db src/server/vm2/cyrusconfig.db

# -------------------------------------------------------------------

setup:
	vim -o -N res/raw/cyrusconfig.db res/raw/top.db src/server/vm1/cyrusconfig.db src/server/vm1/test.db src/server/vm2/curconfig.db src/server/vm2/allconfig.db src/server/vm2/test.db

showtestresults:
	sleep 1
	egrep -i 'running rule|scan|failed|error|exception|fired|xxxxx' src/server/vm2/cyrus.log

whappen:
	vim -o -N src/server/vm1/cyrus.log src/server/vm2/cyrus.log src/server/vm1/cyrus.db src/server/vm2/cyrus.db

logboth:
	xterm -geometry 97x50+0+80 -e make logcat &
	xterm -geometry 97x20+0+80 -e make logout1 &

logthree:
	xterm -geometry 97x50+0+80 -e make logcat &
	xterm -geometry 97x20+0+80 -e make logout1 &
	xterm -geometry 97x20+0+80 -e make logout2 &

logcat:
	adb -e logcat | tee ,logcat | egrep -vi "locapi|\<rpc\>"

lancat:
	adb -d logcat | tee ,logcat | egrep -vi "locapi|\<rpc\>"

logout1:
	tail -9999f src/server/vm1/cyrus.log

logout2:
	tail -9999f src/server/vm2/cyrus.log

# -------------------------------------------------------------------

classes: \
./build/classes/cyrus/Cyrus.class \
./build/classes/cyrus/lib/JSON.class \
./build/classes/cyrus/lib/TestJSON.class \
./build/classes/cyrus/lib/Utils.class \
./build/classes/cyrus/forest/WebObject.class \
./build/classes/cyrus/forest/FunctionalObserver.class \
./build/classes/cyrus/forest/CyrusLanguage.class \
./build/classes/cyrus/forest/Persistence.class \
./build/classes/cyrus/types/Time.class \
./build/classes/cyrus/types/PresenceTracker.class \
./build/classes/server/types/UserHome.class \
./build/classes/server/types/DynamicFile.class \


otherclasses: \
./build/classes/server/types/Twitter.class \


LIBOPTIONS= -Xlint:unchecked -classpath ./src -d ./build/classes

./build/classes/%.class: ./src/%.java
	javac $(LIBOPTIONS) $<

./build/classes:
	mkdir -p ./build/classes

jar: ./build/classes classes
	( cd ./build/classes; jar cfm ../cyrus.jar ../META-INF/MANIFEST.MF . )

# -------------------------------------------------------------------

init:   proguard.cfg local.properties

proguard.cfg:
	android update project -p .

local.properties:
	android update project -p .

kill:
	@-pkill -f 'java -classpath'

clean:
	rm -rf ./build/classes/cyrus
	rm -rf ./build/classes/server
	rm -f  ./src/server/vm?/*.class
	rm -rf bin/classes bin/classes.dex
	rm -f  bin/Cyrus.ap_ bin/Cyrus*un*ed.apk
	rm -f  gen/cyrus/gui/R.java
	-mv ,l* doc/local
	rm -f  ,*

veryclean: kill clean setappemuconfig netconfig setvm3cleanconfig setemumapkey
	-mv    src/server/vm[12]/cyrus.log doc/local
	rm -f  src/server/vm[12]/cyrus.db
	rm -f  src/server/vm2/cyrusconfig.db
	rm -rf bin gen

# -------------------------------------------------------------------


