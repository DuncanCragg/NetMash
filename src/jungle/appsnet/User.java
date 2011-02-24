
package jungle.appsnet;

import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    public User(){}

    public void evaluate(){
        if(contentListContains("is", "user")){
        }
    }

}

