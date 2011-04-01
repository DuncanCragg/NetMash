
package android.gui;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

import android.app.Activity;
import android.os.*;
import android.net.*;
import android.content.Context;
import android.graphics.*;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import static android.view.ViewGroup.LayoutParams.*;

import netmash.platform.Kernel;
import netmash.lib.JSON;
import netmash.forest.FunctionalObserver;

import android.User;

/**  NetMash main.
  */
public class NetMash extends Activity implements OnClickListener, OnKeyListener {

    static public NetMash top=null;
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ECLAIR &&
           keyCode==KeyEvent.KEYCODE_BACK && event.getRepeatCount()==0){
            onBackPressed();
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onBackPressed() {
        user.jumpBack();
        return;
    }

    //---------------------------------------------------------

    private RelativeLayout layout;
    private LinearLayout   buttons;
    private Button         addLinkButton, homeButton;
    private float          screenDensity;
    private int            screenWidth;

    public void drawInitialView(){

        screenDensity = getResources().getDisplayMetrics().density;
        screenWidth   = getResources().getDisplayMetrics().widthPixels;

        setContentView(R.layout.main);
        layout = (RelativeLayout)findViewById(R.id.Layout);
        layout.setBackgroundColor(0xff000000);

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

    private void uiDrawJSON(){ System.out.println("uiDrawJSON:\n"+uiJSON);
        HashMap<String,Object> hm=uiJSON.hashPathN("view");
        boolean vertical="vertical".equals(hm.get("direction"));
        View view;
        if(vertical){
            view=createVerticalStrip(hm);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
            int bottomMargin = buttons.getHeight()*2; if(bottomMargin==0) bottomMargin=80;
            lp.setMargins(0,5,5,bottomMargin);
            view.setLayoutParams(lp);
        }
        else{
            view=createHorizontalStrip(hm);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0,5,5,0);
            view.setLayoutParams(lp);
        }
        View v=layout.getChildAt(0);
        if(v!=null) layout.removeView(v);
        layout.addView(view, 0);
    }

    private View createVerticalStrip(HashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, hm);
        ScrollView view = new ScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createHorizontalStrip(HashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, hm);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createVerticalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, ll);
        ScrollView view = new ScrollView(this);
        view.addView(layout);
        return view;
    }

    private View createHorizontalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, ll);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.addView(layout);
        return view;
    }

    private void fillStrip(LinearLayout layout, HashMap<String,Object> hm){
        float dim0=0;
        float dimn=0;
        int i=0;
        for(String tag: hm.keySet()){
            if(tag.equals("direction")) continue;
            if(tag.equals("options")) continue;
            if(tag.equals("proportions")){
                dim0=parseFirstInt(hm.get(tag).toString());
                continue;
            }
            i++;
        }
        if(dim0 >0 && i>1) dimn=(98-dim0)/(i-1);
        i=0;
        for(String tag: hm.keySet()){
            if(tag.equals("direction")) continue;
            if(tag.equals("options")) continue;
            if(tag.equals("proportions")) continue;
            addToLayout(layout, hm.get(tag), i==0? dim0: dimn);
            i++;
        }
    }

    private void fillStrip(LinearLayout layout, LinkedList ll){
        float dim0=0;
        float dimn=0;
        int i=0;
        for(Object o: ll){
            if(o instanceof String){
                if(o.toString().startsWith("direction:")) continue;
                if(o.toString().startsWith("options:")) continue;
                if(o.toString().startsWith("proportions:")){
                    dim0=parseFirstInt(o.toString());
                    continue;
                }
            }
            i++;
        }
        if(dim0 >0 && i>1) dimn=(98-dim0)/(i-1);
        i=0;
        for(Object o: ll){
            if(o instanceof String){
                if(o.toString().startsWith("direction:")) continue;
                if(o.toString().startsWith("options:")) continue;
                if(o.toString().startsWith("proportions:")) continue;
            }
            addToLayout(layout, o, i==0? dim0: dimn);
            i++;
        }
    }

    private void addToLayout(LinearLayout layout, Object o, float prop){
        if(o==null) return;
        if(o instanceof LinkedHashMap){
            LinkedHashMap<String,Object> hm=(LinkedHashMap<String,Object>)o;
            if("horizontal".equals(hm.get("direction"))) addHorizontalStrip(layout, createHorizontalStrip(hm), prop);
            else                                         addVerticalStrip(  layout, createVerticalStrip(hm), prop);
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            if(ll.size()==0) return;
            if("direction:horizontal".equals(ll.get(0).toString())) addHorizontalStrip(layout, createHorizontalStrip(ll), prop);
            else                                                    addVerticalStrip(  layout, createVerticalStrip(ll), prop);
        }
        else{
            String s=o.toString();
            boolean isUID   =  s.startsWith("uid-");
            boolean isImage = (s.startsWith("http://") && s.endsWith(".jpg"));
            View v = isUID? createUIDView(s): (isImage? createImageView(s): createTextView(s));
            addAView(layout, v, prop);
        }
    }

    private void addHorizontalStrip(LinearLayout layout, View view, float prop){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addVerticalStrip(LinearLayout layout, View view, float prop){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addAView(LinearLayout layout, View view, float prop){
        int width=(prop==0? FILL_PARENT: (int)((prop/100.0)*screenWidth+0.5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, FILL_PARENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private TextView createUIDView(final String s){
        TextView view=new TextView(this);
        view.setText(">>");
        view.setOnClickListener(new OnClickListener(){ public void onClick(View v){
            user.jumpToUID(s);
        }});
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setTextSize(30);
        view.setBackgroundDrawable(getResources().getDrawable(R.drawable.web2box));
        view.setTextColor(0xff000000);
        return view;
    }

    private TextView createTextView(String s){
        TextView view=new TextView(this);
        view.setText(s);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setTextSize(20);
        view.setBackgroundDrawable(getResources().getDrawable(R.drawable.web2box));
        view.setTextColor(0xff000000);
        return view;
    }

    private ImageView createImageView(String url){
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setPadding(8, 8, 8, 8);
        view.setImageBitmap(getImageBitmap(url));
        return view;
    }

    private Bitmap getImageBitmap(String url) {
        Bitmap bm=null;
        try{
            URLConnection conn = new URL(url).openConnection();
            conn.connect();
            InputStream is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is, 8092);
            bm = BitmapFactory.decodeStream(bis);
            bis.close(); is.close();
        } catch (IOException e) {
            System.err.println("Couldn't load image at "+url+"\n"+e);
        }
        return bm;
    } 

    static public final String  INTRE = ".*?([0-9]+).*";
    static public final Pattern INTPA = Pattern.compile(INTRE);
    private int parseFirstInt(String s){
        Matcher m = INTPA.matcher(s);
        if(!m.matches()) return 0;
        int r = Integer.parseInt(m.group(1));
        return r;
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

        InputStream configis = getResources().openRawResource(R.raw.netmashconfig);
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

