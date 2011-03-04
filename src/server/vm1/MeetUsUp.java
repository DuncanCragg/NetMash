
import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

/** Meet Us Up application server side.
  */
public class MeetUsUp extends WebObject {

    public MeetUsUp(){}

    public void evaluate(){
        if(contentListContains("is", "meetusup")){
            testit();
        }
    }

    private void testit(){
        for(String queryuid: alerted()){
        }
    }
}

