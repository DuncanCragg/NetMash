
package jungle;

import jungle.Version;
import jungle.platform.Kernel;
import jungle.forest.FunctionalObserver;

/**  Jungle: FOREST Reference Implementation; server main.
  */
public class Jungle {

    //-----------------------------------------------------

    static public void main(String[] args){

        System.out.println("-------------------");
        System.out.println(Version.NAME+" "+Version.NUMBERS);

        Kernel.init(new FunctionalObserver());
        Kernel.run();
    }
}

//---------------------------------------------------------

