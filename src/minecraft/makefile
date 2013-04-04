
cr: compile runhere


crr: compile release runthere


compile:
	./recompile.sh --client

runhere:
	./startclient.sh

release:
	./reobfuscate.sh --client
	cp lib/*.db reobf/minecraft
	sed -i 's:net.minecraft.src.CyrusWorld:CyrusWorld:' reobf/minecraft/top.db
	mkdir -p reobf/minecraft/statics
	bash -c "cp -r lib/statics/{index.html,css,js,cyrus.appcache,favicon.ico} reobf/minecraft/statics"
	rm -f cyrus.zip
	cd reobf/minecraft/ && zip -r ../../cyrus.zip *
	cp cyrus.zip ~/.minecraft/mods/

runthere:
	cd ~/.minecraft && java -Xmx1024M -Xms512M -Djava.security.egd=file:/dev/random -jar minecraft.jar

editor:
	chromium-browser 'http://localhost:8084/#http://localhost:8084/o/uid-fb97-b02c-e69b-90dd.json'


