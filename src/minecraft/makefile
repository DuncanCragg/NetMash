
SERVER_RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/cyrusserver.jar
CLIENT_RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/1.6.2-Cyrus.jar
MINECRAFT_DIR=~/.minecraft

small:
	sed -i 's/overrideWidth:670/overrideWidth:400/'  jars/options.txt
	sed -i 's/overrideHeight:800/overrideHeight:300/' jars/options.txt

large:
	sed -i 's/overrideWidth:400/overrideWidth:670/'  jars/options.txt
	sed -i 's/overrideHeight:300/overrideHeight:800/' jars/options.txt

test-server: compile-server cleandb run-server


test-client: compile-client cleandb run-client


clean-client: cleandb run-client


cont-client: compile-client run-client


run-release-server: compile-server cyrusserver-jar release-server run-new-server


run-release-client: compile-client cyrusclient-jar release-client run-new-client


compile-server:
	./recompile.sh --server

compile-client:
	./recompile.sh --client

cleandb:
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
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#'           reobf/minecraft/cyrusconfig.db
	sed -i 's#http://lxcalhost#http://the-cyrus.net#'           reobf/minecraft/cyrusconfig.db
	sed -i 's#port: 8084#port: 8085#'                           reobf/minecraft/cyrusconfig.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrusclient.zip
	cd reobf/minecraft/ && zip -rq ../../cyrusclient.zip *

cyrusclient-jar: cyrusclient-zip $(MINECRAFT_DIR)/versions/1.6.2-Cyrus
	rm -f 1.6.2-Cyrus.jar
	mkdir tmp
	cd tmp && unzip -q ../jars/1.6.2-Cyrus.jar
	cd tmp && rm -rf META-INF/
	cd tmp && unzip -oq ../cyrusclient.zip
	cd tmp && jar -cf ../1.6.2-Cyrus.jar *
	rm -rf tmp
	cp 1.6.2-Cyrus.jar $(MINECRAFT_DIR)/versions/1.6.2-Cyrus

release-server:
	cp cyrusserver.jar $(SERVER_RELEASE_TARGET)

release-client:
	cp 1.6.2-Cyrus.jar $(CLIENT_RELEASE_TARGET)

run-new-server: cyrusserver.jar
	mkdir -p runserver
	rm runserver/cyrus.db
	cd runserver && java -jar ../cyrusserver.jar

run-new-client: $(MINECRAFT_DIR)/versions/1.6.2-Cyrus/1.6.2-Cyrus.jar
	rm $(MINECRAFT_DIR)/cyrus.db
	java -jar $(MINECRAFT_DIR)/minecraft.jar

$(MINECRAFT_DIR)/versions/1.6.2-Cyrus:
	cd $(MINECRAFT_DIR)/versions/ && cp -a 1.6.2 1.6.2-Cyrus
	cd $(MINECRAFT_DIR)/versions/1.6.2-Cyrus && mv 1.6.2.jar                  1.6.2-Cyrus.jar
	cd $(MINECRAFT_DIR)/versions/1.6.2-Cyrus && mv 1.6.2.json                 1.6.2-Cyrus.json
	cd $(MINECRAFT_DIR)/versions/1.6.2-Cyrus && sed -i "s:1.6.2:1.6.2-Cyrus:" 1.6.2-Cyrus.json
	cp $(MINECRAFT_DIR)/versions/1.6.2-Cyrus/1.6.2-Cyrus.jar jars

mc:
	java -jar $(MINECRAFT_DIR)/minecraft.jar

mc2:
	java -Dsun.java3d.opengl=true -Xmx1024M -Xms512M -jar $(MINECRAFT_DIR)/minecraft.jar

mc3:
	java -Dsun.java3d.opengl=true -Xmx1024M -Xms1024M -XX:+UseFastAccessorMethods -XX:+AggressiveOpts -XX:+DisableExplicitGC -XX:+UseAdaptiveGCBoundary -XX:MaxGCPauseMillis=500 -XX:SurvivorRatio=16 -XX:+UseParallelGC -XX:UseSSE=3 -XX:ParallelGCThreads=4 -jar $(MINECRAFT_DIR)/minecraft.jar
clean:
	rm -f cyrusclient.zip 1.6.2-Cyrus.jar cyrusserver.zip cyrusserver.jar


