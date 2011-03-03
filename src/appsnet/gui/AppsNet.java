
package appsnet.gui;

import java.util.*;
import java.io.*;

import android.app.Activity;
import android.os.*;
import android.content.Context;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import static android.view.ViewGroup.LayoutParams.*;

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

    private RelativeLayout layout;
    private LinearLayout   buttons;
    private Button         addLinkButton, homeButton;

    public void drawInitialView(){

        setContentView(R.layout.main);
        layout = (RelativeLayout)findViewById(R.id.Layout);

        buttons = (LinearLayout)findViewById(R.id.Buttons);

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
    public void drawJSON(JSON uiJSON){ System.out.println("drawJSON");
        this.uiJSON=uiJSON;
        guiHandler.post(uiDrawJSONRunnable);
    }

    /** UI Thread runs here. */
    private void uiDrawJSON(){
        LinkedList ll=uiJSON.listPathN("view");
        boolean vertical=false;
        View strip;
        if(vertical){
            strip=createVerticalStrip(ll);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
            int bottomMargin = buttons.getHeight()*2; if(bottomMargin==0) bottomMargin=50;
            lp.setMargins(0, 0, 0, bottomMargin);
            strip.setLayoutParams(lp);
        }
        else{
            strip=createHorizontalStrip(ll);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 0);
            strip.setLayoutParams(lp);
        }
        View v=layout.getChildAt(0);
        if(v!=null) layout.removeView(v);
        layout.addView(strip, 0);
    }

    private View createVerticalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        fillStrip(layout, ll);
        ScrollView strip = new ScrollView(this);
        strip.addView(layout);
        return strip;
    }

    private View createHorizontalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        fillStrip(layout, ll);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.addView(layout);
        return strip;
    }

    private void fillStrip(LinearLayout layout, LinkedList ll){
        for(Object o: ll){
            TextView tv=new TextView(this);
            tv.setText("--"+o+"--");
            tv.setPadding(40, 40, 40, 40);
            layout.addView(tv);
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

