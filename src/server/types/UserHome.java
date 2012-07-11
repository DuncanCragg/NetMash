package server.types;

import netmash.forest.WebObject;

/** User Home collection.
  */
public class UserHome extends WebObject {

    public UserHome(){}

    public void evaluate(){
        if(contentListContains("is", "userhome")){
            testit();
        }
    }

    private void testit(){
        for(String useruid: alerted()){ logrule();
            contentSetAdd("list", useruid);
        }
    }
}

