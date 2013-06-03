
RELEASE_TARGET=~/the-u-web/net/the-cyrus.net/cyrus.zip

small:
	sed -i 's/overrideWidth:670/overrideWidth:300/'  jars/options.txt
	sed -i 's/overrideHeight:800/overrideHeight:200/' jars/options.txt

large:
	sed -i 's/overrideWidth:300/overrideWidth:670/'  jars/options.txt
	sed -i 's/overrideHeight:200/overrideHeight:800/' jars/options.txt

cr: compile runhere


cer: compile emptydb runhere


crr: compile release runthere


compile:
	./recompile.sh --client

emptydb:
	rm -f jars/cyrus.db

runhere:
	./startclient.sh

release:
	./reobfuscate.sh --client
	cp lib/*.db reobf/minecraft
	sed -i 's:net.minecraft.src.Minecraft:Minecraft:' reobf/minecraft/top.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrus.zip
	cd reobf/minecraft/ && zip -r ../../cyrus.zip *
	cp cyrus.zip $(RELEASE_TARGET)

runthere:
	cp cyrus.zip ~/.minecraft/mods/
	rm -f ~/.minecraft/cyrus.db
	cd ~/.minecraft && java -Xmx1024M -Xms512M -Djava.security.egd=file:/dev/random -jar minecraft.jar

runmc:
	rm -f ~/.minecraft/mods/cyrus.zip
	cd ~/.minecraft && java -Xmx1024M -Xms512M -Djava.security.egd=file:/dev/random -jar minecraft.jar

editor:
	chromium-browser 'http://localhost:8084/#http://localhost:8084/o/uid-1044-e6e9-125e-e35c.json'


