
SERVER_RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/cyrusserver.jar
CLIENT_RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/cyrusclient.zip

small:
	sed -i 's/overrideWidth:670/overrideWidth:300/'  jars/options.txt
	sed -i 's/overrideHeight:800/overrideHeight:300/' jars/options.txt

large:
	sed -i 's/overrideWidth:300/overrideWidth:670/'  jars/options.txt
	sed -i 's/overrideHeight:300/overrideHeight:800/' jars/options.txt

test-server: compile-server emptydb run-server


test-client: compile-client emptydb run-client


cont-client: compile-client run-client


run-release-server: compile-server release-server-jar release-server run-new-server


run-release-client: compile-client release-client-zip release-client run-new-client


compile-server:
	./recompile.sh --server

compile-client:
	./recompile.sh --client

emptydb:
	rm -f jars/cyrus.db

run-server:
	./startserver.sh

run-client:
	./startclient.sh

release-server-jar: build-server-zip build-server-jar

build-server-zip:
	./reobfuscate.sh --server
	cp lib/cyrusconfig.db reobf/minecraft_server
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#' reobf/minecraft_server/cyrusconfig.db
	sed -i 's#http://localhost#http://the-cyrus.net#' reobf/minecraft_server/cyrusconfig.db
	mkdir -p reobf/minecraft_server/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft_server/statics"
	rm -f cyrusserver.zip
	cd reobf/minecraft_server/ && zip -r ../../cyrusserver.zip *

build-server-jar:
	rm -f cyrusserver.jar
	mkdir tmp
	cd tmp && unzip ../jars/minecraft_server.1.6.2.jar
	cd tmp && unzip -o ../cyrusserver.zip
	cd tmp && jar -cfm ../cyrusserver.jar META-INF/MANIFEST.MF *
	rm -rf tmp

release-client-zip:
	./reobfuscate.sh --client
	cp lib/cyrusconfig.db reobf/minecraft
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#' reobf/minecraft/cyrusconfig.db
	sed -i 's#http://localhost#http://the-cyrus.net#' reobf/minecraft/cyrusconfig.db
	sed -i 's#port: 8084#port: 8085#'                 reobf/minecraft/cyrusconfig.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrusclient.zip
	cd reobf/minecraft/ && zip -r ../../cyrusclient.zip *

release-server:
	cp cyrusserver.jar $(SERVER_RELEASE_TARGET)

release-client:
	cp cyrusclient.zip $(CLIENT_RELEASE_TARGET)

run-new-server:
	mkdir -p runserver && cd runserver && java -jar ../cyrusserver.jar

run-new-client:
	cp cyrusclient.zip ~/.minecraft/versions/1.6.2ML/mods/
	rm -f ~/.minecraft/cyrus.db
	java -jar ~/.minecraft/minecraft.jar

continue-release-client:
	cp cyrusclient.zip ~/.minecraft/versions/1.6.2ML/mods/
	java -jar ~/.minecraft/minecraft.jar

mc:
	java -jar ~/.minecraft/minecraft.jar

