
import java.util.*;
import cyrus.forest.WebObject;
import cyrus.forest.CyrusLanguage;

import static cyrus.lib.Utils.*;

/** FX Dealer Object from Book Chapter example.
  * Run by CyrusLanguage rules, but have a pseudo-market ticker.
  */
public class FXDealerTicket extends CyrusLanguage {

    public FXDealerTicket(){}

    static boolean running=false;

    public void evaluate(){
        super.evaluate();
        if(!running && contentListContains("is", "ticket")) setUpPseudoMarketMoverInterfaceCallback();
    }

    static private double[] prices = { 81.8, 81.6 };

    private void setUpPseudoMarketMoverInterfaceCallback(){
        running=true;
        new Thread(){ public void run(){
            for(int i=0; i<prices.length; i++){
                try{ Thread.sleep(500); }catch(Exception e){}
                marketMoved(prices[i]);
            }
        } }.start();
    }

    private void marketMoved(final double price){
        new Evaluator(this){
            public void evaluate(){ logrule();
                contentDouble("ask", price);
                self.evaluate();
            }
        };
    }

    // ----------------------------------------------------
}

