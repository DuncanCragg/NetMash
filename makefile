
classes: \
./build/classes/Jungle.class \
./build/classes/jungle/platform/Kernel.class \
./build/classes/jungle/platform/Module.class \
./build/classes/jungle/platform/ChannelUser.class \
./build/classes/jungle/platform/FileUser.class \
./build/classes/jungle/lib/JSON.class \
./build/classes/jungle/lib/TestJSON.class \
./build/classes/jungle/forest/UID.class \
./build/classes/jungle/forest/FunctionalObserver.class \
./build/classes/jungle/forest/WebObject.class \
./build/classes/jungle/forest/Persistence.class \
./build/classes/jungle/forest/HTTP.class \


LIBOPTIONS= -Xlint:unchecked -classpath ./src -d ./build/classes

./build/classes/%.class: ./src/%.java
	javac $(LIBOPTIONS) $<

run1: jar kill
	(cd vm1; ./run.sh)

run2: jar kill
	(cd vm2; ./run.sh)

runall: run1 run2

runon1: jar kill
	( cd vm1 ; java -classpath .:../build/jungle.jar Jungle & )

runon2: jar kill
	( cd vm2 ; java -classpath .:../build/jungle.jar Jungle & )

whappen:
	vim -o -N vm1/forest.db vm1/jungle.log vm2/forest.db vm2/jungle.log

setup:
	vim -o -N vm1/jungle-config.json vm1/test-forest.db vm2/jungle-config.json vm2/test-forest.db

kill:
	-pkill java

check: clean runtests

runtests: runjson runuid

runjson: jar
	java -ea -classpath ./build/jungle.jar jungle.lib.TestJSON

runuid: jar
	java -ea -classpath ./build/jungle.jar jungle.forest.UID

jar: classes
	( cd ./build/classes; jar cfm ../jungle.jar ../META-INF/MANIFEST.MF . )

clean:
	rm -rf ./build/classes/*.class ./build/classes/jungle
	rm -rf ./vm*/*.class
	rm -rf ./vm*/forest.db


