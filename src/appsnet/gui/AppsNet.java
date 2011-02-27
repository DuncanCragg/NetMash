
package appsnet.gui;

import java.io.*;

import android.app.Activity;
import android.os.*;
import android.content.Context;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import jungle.platform.Kernel;
import jungle.lib.JSON;
import jungle.forest.FunctionalObserver;

import appsnet.User;

/**  AppsNet main.
  */
public class AppsNet extends Activity implements OnClickListener, OnKeyListener {

    static public AppsNet top=null;
    static public User    user=null;

    public void onUserReady(User u){ user = u; }

    //---------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); System.out.println("onCreate");
        top=this;
        if(!Kernel.running) runKernel();
        drawInitialView();
        user.onTopCreate();
    }

    @Override
    public void onRestart(){
        super.onRestart(); System.out.println("onRestart");
    }

    @Override
    public void onStart(){
        super.onStart(); System.out.println("onStart");
    }

    @Override
    public void onResume(){
        super.onResume(); System.out.println("onResume");
        user.onTopResume();
    }

    @Override
    public void onPause(){
        super.onPause(); System.out.println("onPause");
        user.onTopPause();
    }

    @Override
    public void onStop(){
        super.onStop(); System.out.println("onStop");
    }

    @Override
    public void onDestroy(){
        super.onDestroy(); System.out.println("onDestroy");
        user.onTopDestroy();
        top=null;
    }

    //---------------------------------------------------------

    private LinearLayout content;
    private LinearLayout.LayoutParams layoutParams;
    private Button addLinkButton, homeButton;

    public void drawInitialView(){

        setContentView(R.layout.main);
        content = (LinearLayout)findViewById(R.id.Content);

        layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                                                     LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(5, 10, 5, 0);

        addLinkButton = (Button)findViewById(R.id.AddLinkButton);
        addLinkButton.setOnClickListener(this);

        homeButton = (Button)findViewById(R.id.HomeButton);
        homeButton.setOnClickListener(this);
    }

    //---------------------------------------------------------

    private JSON uiJSON;
    private Handler guiHandler = new Handler();
    private Runnable uiDrawJSONRunnable=new Runnable(){public void run(){uiDrawJSON();}};

    /** Non-UI Thread comes in here. */
    public void drawJSON(JSON uiJSON){
        this.uiJSON=uiJSON;
        guiHandler.post(uiDrawJSONRunnable);
    }

    /** UI Thread runs here. */
    private void uiDrawJSON(){
        for(Object o: uiJSON.listPathN("view")){
            Button b=new Button(top);
            b.setTextSize(15);
            b.setText("[ "+o+" ]");
            content.addView(b, layoutParams);
        }
    }

    //---------------------------------------------------------

    public void onClick(View v){
        switch (v.getId()){
        case R.id.AddLinkButton:
            Toast.makeText(this, "Adding links is not implemented yet! :-)", Toast.LENGTH_SHORT).show();
            break;
        case R.id.HomeButton:
            Toast.makeText(this, "Home page is not implemented yet! :-)", Toast.LENGTH_SHORT).show();
            break;
        }
    }

    public boolean onKey(View v, int keyCode, KeyEvent event){
        return true;
    }

    //---------------------------------------------------------

    private void runKernel(){

        InputStream configis = getResources().openRawResource(R.raw.jungleconfig);
        JSON config=null;
        try{ config = new JSON(configis); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        String db = config.stringPathN("persist:db");

        InputStream topdbis=null;
        try{ topdbis = openFileInput(db); }catch(Exception e){ }
        if(topdbis==null) topdbis = getResources().openRawResource(R.raw.topdb);

        FileOutputStream topdbos=null;
        try{ topdbos = openFileOutput(db, Context.MODE_APPEND); }catch(Exception e){ throw new RuntimeException("Local DB: "+e); }

        workaroundForFroyoBug();

        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }

    private void workaroundForFroyoBug(){
        /* http://code.google.com/p/android/issues/detail?id=9431 */
        java.lang.System.setProperty("java.net.preferIPv4Stack",     "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
    }
}

//---------------------------------------------------------

