
package cyrus;

import cyrus.Version;
import cyrus.platform.Kernel;
import cyrus.forest.FunctionalObserver;

/**  NetMash: Cyrus/Object Network Reference Implementation; server main.
  */
public class NetMash {

    //-----------------------------------------------------

    static public void main(String[] args){

        System.out.println("-------------------");
        System.out.println(Version.NAME+" "+Version.NUMBERS);

        Kernel.init(new FunctionalObserver());
        Kernel.run();
    }
}

//---------------------------------------------------------

