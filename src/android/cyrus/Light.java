package cyrus;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to drive the RGB indicator LED.
  */
public class Light extends CyrusLanguage {

    public Light(){
        super("{ is: editable 3d cuboid light\n"+
              "  Rules: { is: 3d rule editable\n"+
              "    Notifying: => @. with @within\n"+
              "  }\n"+
              "  title: Light\n"+
              "  rotation: 45 45 45\n"+
              "  scale: 1 1 1\n"+
              "  light: 1 1 0\n"+
              "  position: 0 0 0\n"+
              "}\n", true);
    }
}

