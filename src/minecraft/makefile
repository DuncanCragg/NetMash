
RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/cyrus.zip

small:
	sed -i 's/overrideWidth:670/overrideWidth:300/'  jars/options.txt
	sed -i 's/overrideHeight:800/overrideHeight:300/' jars/options.txt

large:
	sed -i 's/overrideWidth:300/overrideWidth:670/'  jars/options.txt
	sed -i 's/overrideHeight:300/overrideHeight:800/' jars/options.txt

test: compile emptydb runclient


cont: compile runclient


run: compile release-client-zip release newcyrus


compile:
	./recompile.sh --client

emptydb:
	rm -f jars/cyrus.db

runclient:
	./startclient.sh

release-client-zip:
	./reobfuscate.sh --client
	cp lib/cyrusconfig.db reobf/minecraft
	sed -i 's#net.minecraft.src.Minecraft#Minecraft#' reobf/minecraft/cyrusconfig.db
	sed -i 's#http://localhost#http://the-cyrus.net#' reobf/minecraft/cyrusconfig.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrus.zip
	cd reobf/minecraft/ && zip -r ../../cyrus.zip *

release:
	cp cyrus.zip $(RELEASE_TARGET)

newcyrus:
	cp cyrus.zip ~/.minecraft/versions/1.6.2ML/mods/
	rm -f ~/.minecraft/cyrus.db
	java -jar ~/.minecraft/minecraft.jar

continue:
	cp cyrus.zip ~/.minecraft/versions/1.6.2ML/mods/
	java -jar ~/.minecraft/minecraft.jar

nocyrus:
	rm -f ~/.minecraft/mods/versions/1.6.2ML/cyrus.zip
	java -jar ~/.minecraft/minecraft.jar


