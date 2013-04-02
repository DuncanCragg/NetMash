
cr: compile runhere


crr: compile release runthere


compile:
	./recompile.sh --client

runhere:
	./startclient.sh

release:
	./reobfuscate.sh --client
	cd reobf/minecraft/ && zip -r ../../cyrus.zip *
	cp cyrus.zip ~/.minecraft/mods/

runthere:
	java -Xmx1024M -Xms512M -Djava.security.egd=file:/dev/random -jar ../../minecraft.jar



