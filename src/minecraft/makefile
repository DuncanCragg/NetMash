
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


run-release-server: compile-server cyrusserver-jar release-server run-new-server


run-release-client: compile-client cyrusclient-zip release-client run-new-client


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

cyrusserver-zip:
	./reobfuscate.sh --server
	cp lib/cyrusconfig.db reobf/minecraft_server
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#' reobf/minecraft_server/cyrusconfig.db
	sed -i 's#http://lxcalhost#http://the-cyrus.net#' reobf/minecraft_server/cyrusconfig.db
	mkdir -p reobf/minecraft_server/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft_server/statics"
	rm -f cyrusserver.zip
	cd reobf/minecraft_server/ && zip -rq ../../cyrusserver.zip *

cyrusserver-jar: cyrusserver-zip
	rm -f cyrusserver.jar
	mkdir tmp
	cd tmp && unzip -q ../jars/minecraft_server.1.6.2.jar
	cd tmp && unzip -oq ../cyrusserver.zip
	cd tmp && jar -cfm ../cyrusserver.jar META-INF/MANIFEST.MF *
	rm -rf tmp

cyrusclient-zip:
	./reobfuscate.sh --client
	cp lib/cyrusconfig.db reobf/minecraft
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#' reobf/minecraft/cyrusconfig.db
	sed -i 's#http://lxcalhost#http://the-cyrus.net#' reobf/minecraft/cyrusconfig.db
	sed -i 's#port: 8084#port: 8085#'                 reobf/minecraft/cyrusconfig.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrusclient.zip
	cd reobf/minecraft/ && zip -rq ../../cyrusclient.zip *

release-server:
	cp cyrusserver.jar $(SERVER_RELEASE_TARGET)

release-client:
	cp cyrusclient.zip $(CLIENT_RELEASE_TARGET)

run-new-server: cyrusserver.jar
	rm -f runserver/cyrus.db
	mkdir -p runserver && cd runserver && java -jar ../cyrusserver.jar

run-new-client: build-client-jar
	rm -f ~/.minecraft/cyrus.db
	java -jar ~/.minecraft/minecraft.jar

build-client-jar: cyrusclient.zip ~/.minecraft/versions/1.6.2-Cyrus
	cd ~/.minecraft/versions/1.6.2-Cyrus && mkdir tmp
	cd ~/.minecraft/versions/1.6.2-Cyrus/tmp && unzip -q ../1.6.2-Cyrus.jar
	cd ~/.minecraft/versions/1.6.2-Cyrus/tmp && rm ../1.6.2-Cyrus.jar && rm -rf META-INF/
	cp cyrusclient.zip ~/.minecraft/versions/1.6.2-Cyrus/tmp
	cd ~/.minecraft/versions/1.6.2-Cyrus/tmp && unzip -oq cyrusclient.zip
	cd ~/.minecraft/versions/1.6.2-Cyrus/tmp && jar -cf ../1.6.2-Cyrus.jar *
	cd ~/.minecraft/versions/1.6.2-Cyrus && rm -rf tmp

~/.minecraft/versions/1.6.2-Cyrus:
	cd ~/.minecraft/versions/ && cp -a 1.6.2 1.6.2-Cyrus
	cd ~/.minecraft/versions/1.6.2-Cyrus && mv 1.6.2.jar                  1.6.2-Cyrus.jar
	cd ~/.minecraft/versions/1.6.2-Cyrus && mv 1.6.2.json                 1.6.2-Cyrus.json
	cd ~/.minecraft/versions/1.6.2-Cyrus && sed -i "s:1.6.2:1.6.2-Cyrus:" 1.6.2-Cyrus.json

mc:
	java -jar ~/.minecraft/minecraft.jar

clean:
	rm -f cyrusclient.zip cyrusserver.zip cyrusserver.jar


