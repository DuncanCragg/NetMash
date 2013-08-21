
RELEASE_TARGET5=~/the-u-web/net/the-cyrus.net/cyrus5.zip
RELEASE_TARGET6=~/the-u-web/net/the-cyrus.net/cyrus6.zip

small:
	sed -i 's/overrideWidth:670/overrideWidth:300/'  jars/options.txt
	sed -i 's/overrideHeight:800/overrideHeight:300/' jars/options.txt

large:
	sed -i 's/overrideWidth:300/overrideWidth:670/'  jars/options.txt
	sed -i 's/overrideHeight:300/overrideHeight:800/' jars/options.txt

test: compile emptydb runclient


cont: compile runclient


run5: compile release-client-zip release5 newcyrus5


run6: compile release-client-zip release6 newcyrus6


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

release5:
	cp cyrus.zip $(RELEASE_TARGET5)

release6:
	cp cyrus.zip $(RELEASE_TARGET6)

newcyrus5:
	cp cyrus.zip ~/.minecraft/mods/
	rm -f ~/.minecraft/cyrus.db
	java -jar ~/.minecraft/minecraft.jar

continue5:
	cp cyrus.zip ~/.minecraft/mods/
	java -jar ~/.minecraft/minecraft.jar

nocyrus5:
	rm -f ~/.minecraft/mods/cyrus.zip
	java -jar ~/.minecraft/minecraft.jar

newcyrus6:
	cp cyrus.zip ~/.minecraft/versions/1.6.2ML/mods/
	rm -f ~/.minecraft/cyrus.db
	java -jar ~/.minecraft/minecraft.jar

continue6:
	cp cyrus.zip ~/.minecraft/versions/1.6.2ML/mods/
	java -jar ~/.minecraft/minecraft.jar

nocyrus6:
	rm -f ~/.minecraft/mods/versions/1.6.2ML/cyrus.zip
	java -jar ~/.minecraft/minecraft.jar


