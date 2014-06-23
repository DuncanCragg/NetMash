package cyrus;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to drive the RGB indicator LED.
  */
public class Light extends CyrusLanguage {

    public Light() {}

    public Light(String linksarounduid){
        super("{ is: editable 3d cuboid light\n"+
              "  Rules: http://netmash.net/o/uid-16bd-140a-8862-41cd.cyr\n"+
              "         http://netmash.net/o/uid-0dc6-ad27-05ec-a0b2.cyr\n"+
              "         http://netmash.net/o/uid-e369-6d5d-5283-7bc7.cyr\n"+
              "         http://netmash.net/o/uid-9011-94df-9feb-e3c2.cyr\n"+
              "         http://netmash.net/o/uid-f27f-7d1a-2eab-c547.cyr\n"+
              "  P: { modulator: 1.3 1.3 1.3 }\n"+
              "  Timer: 100\n"+
              "  title: Light\n"+
              "  rotation: 45 45 45\n"+
              "  scale: 1 1 1\n"+
              "  light: 1 1 0\n"+
              "  position: 0 0 0\n"+
              "  links-around: "+linksarounduid+"\n"+
              "}\n", true);
    }

    public void evaluate(){
        String placeURL=content("links-around:place");
        if(placeURL!=null && !contentIs("within", placeURL)){
            contentList("position", list(random(1,10), random(0,3), random(1,10)));
            content("within", placeURL);
            notifying(placeURL);
        }
        super.evaluate();
    }
}

