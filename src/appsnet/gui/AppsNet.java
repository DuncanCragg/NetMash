
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
        layout.setBackgroundColor(0xffffffff);

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

    public void drawJSON(JSON uiJSON){ System.out.println("drawJSON");
        this.uiJSON=uiJSON;
        guiHandler.post(uiDrawJSONRunnable);
    }

    private void uiDrawJSON(){ System.out.println(uiJSON);
        HashMap<String,Object> hm=uiJSON.hashPathN("view");
        boolean vertical=hm.get("direction").equals("vertical");
        View view;
        if(vertical){
            view=createVerticalStrip(hm);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
            int bottomMargin = buttons.getHeight()*2; if(bottomMargin==0) bottomMargin=80;
            lp.setMargins(0, 0, 0, bottomMargin);
            view.setLayoutParams(lp);
        }
        else{
            view=createHorizontalStrip(hm);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 0);
            view.setLayoutParams(lp);
        }
        View v=layout.getChildAt(0);
        if(v!=null) layout.removeView(v);
        layout.addView(view, 0);
    }

    private View createVerticalStrip(HashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setBackgroundColor(0xff000000);
        layout.setOrientation(LinearLayout.VERTICAL);
        fillStrip(layout, hm);
        ScrollView view = new ScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createHorizontalStrip(HashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        fillStrip(layout, hm);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createVerticalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        fillStrip(layout, ll);
        ScrollView view = new ScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createHorizontalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        fillStrip(layout, ll);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.addView(layout);
        return view;
    }

    private void fillStrip(LinearLayout layout, HashMap<String,Object> hm){
        for(String tag: hm.keySet()){
            if(tag.equals("direction")) continue;
            addView(layout, tag);
            Object o=hm.get(tag);
            addView(layout, o);
        }
    }

    private void fillStrip(LinearLayout layout, LinkedList ll){
        for(Object o: ll){
            if(o instanceof String && o.toString().startsWith("direction:")) continue;
            addView(layout, o);
        }
    }

    private void addView(LinearLayout layout, Object o){
        if(o instanceof LinkedHashMap) addVerticalStrip(  layout, createVerticalStrip((LinkedHashMap<String,Object>)o));
        else
        if(o instanceof LinkedList)    addHorizontalStrip(layout, createHorizontalStrip((LinkedList)o));
        else                           addTextView(       layout, createTextView(o.toString()));
    }

    private void addHorizontalStrip(LinearLayout layout, View view){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(2, 1, 2, 1);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addVerticalStrip(LinearLayout layout, View view){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(2, 1, 2, 1);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addTextView(LinearLayout layout, View view){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private TextView createTextView(String s){
        TextView view=new TextView(this);
        view.setText(s);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setPadding(40, 60, 40, 60);
        view.setTextSize(20);
        view.setBackgroundColor(0xffffffff);
        view.setTextColor(0xff000000);
        return view;
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

