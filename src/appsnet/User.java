
package appsnet;

import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import appsnet.gui.*;

/** User viewing the Object Web.
  */
public class User extends WebObject implements OnClickListener, OnKeyListener {

    AppsNet top=null;

    public User(){
        top=AppsNet.top;
        top.user=this;
        onTopCreate();
    }

    public void evaluate(){
        if(contentListContains("is", "user")){
            testThatICanSeeTwitterTopAndPersistenceWorked();
        }
    }

    private void testThatICanSeeTwitterTopAndPersistenceWorked(){
        if(contentList("viewing:is")!=null){
            if(!contentIs("a","b")){ log("viewing:is = "               +contentList("viewing:is")); content("a","b"); }
            else{                    log("viewing:users:Duncancragg = "+contentHash("viewing:users:Duncancragg")); }
        }
    }

    // ------------------------------------------------

    private Button addLinkButton, homeButton;
    public void onTopCreate(){ log("onTopCreate");
        top.setContentView(R.layout.main);
        addLinkButton = (Button)top.findViewById(R.id.AddLinkButton);
        addLinkButton.setOnClickListener(this);
        homeButton = (Button)top.findViewById(R.id.HomeButton);
        homeButton.setOnClickListener(this);
    }

    public void onClick(View v) {
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return true;
    }

    public void onTopDestroy(){
    }

    // ------------------------------------------------
}

