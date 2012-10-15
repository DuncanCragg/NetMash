
package android.gui;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

import android.util.AttributeSet;
import android.app.Activity;
import android.os.*;
import android.net.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.inputmethod.InputMethodManager;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import android.opengl.GLSurfaceView;

import static android.view.ViewGroup.LayoutParams.*;

import com.google.android.maps.*;

import netmash.platform.Kernel;
import netmash.lib.JSON;
import netmash.forest.FunctionalObserver;

import static netmash.lib.Utils.*;

import android.User;

/**  NetMash main.
  */
public class NetMash extends MapActivity{

    static public NetMash top=null;
    static public User    user=null;

    public void onUserReady(User u){ user = u; }

    //---------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); log("onCreate");
        String url = getIntent().getDataString();
        if(url!=null) log("Being created with URL: "+url);
        top=this;
        if(!Kernel.running) runKernel();
        drawInitialView();
        if(user==null) User.createUserAndDevice();
        user.onTopCreate(url);
    }

    @Override
    public void onRestart(){
        super.onRestart(); log("onRestart");
    }

    @Override
    public void onStart(){
        super.onStart(); log("onStart");
    }

    @Override
    public void onResume(){
        super.onResume(); log("onResume");
        user.onTopResume();
        if(onemeshview!=null) onemeshview.onResume();
    }

    @Override
    public void onPause(){
        super.onPause(); log("onPause");
        user.onTopPause();
        if(onemeshview!=null) onemeshview.onPause();
    }

    @Override
    public void onStop(){
        super.onStop(); log("onStop");
    }

    @Override
    public void onDestroy(){
        super.onDestroy(); log("onDestroy");
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
    public void onBackPressed(){
        user.jumpBack();
        return;
    }

    public void getKeys(boolean show){
log(show? "show keyboard": "hide keyboard");
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(show) imm.showSoftInput(onemeshview, 0);
        else     imm.hideSoftInputFromWindow(onemeshview.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
/*
        onemeshview.setOnKeyListener(new OnKeyListener(){
            public boolean onKey(View v, int keyCode, KeyEvent event){
                if(event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.KEYCODE_ENTER){
                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
                    return true;
                }
                return false;
            }
        });
*/
    }

    private float px=0f;
    private float py=0f;
    private float tx=0f;
    private float ty=0f;
    private int   numTouch=0;
    private long  time=0;

    @Override
    public boolean onTouchEvent(MotionEvent e){
        if(onemeshview==null) return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                tx=e.getX(0); ty=e.getY(0);
                px=tx; py=ty;
                numTouch=1;
                time=System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                tx=e.getX(1); ty=e.getY(1);
                px=tx; py=ty;
                numTouch=2;
                break;
            case MotionEvent.ACTION_MOVE:
                if(numTouch==0) break;
                if(time>0){
                    long t=System.currentTimeMillis();
                    if(t-time>500) numTouch=3;
                    time=0;
                }
                float cx=0,cy=0;
                if(numTouch==1){ cx=e.getX(0); cy=e.getY(0); }
                if(numTouch==2){ cx=e.getX(1); cy=e.getY(1); }
                if(numTouch==3){ cx=e.getX(0); cy=e.getY(0); }
                float mx=cx-px, my=cy-py;
                if(mx*mx+my*my<0.1) return true;
                px=cx; py=cy;
                final float dx=100*mx/screenWidth;
                final float dy=100*my/screenHeight;
                onemeshview.queueEvent(new Runnable(){ public void run(){
                    if(onerenderer==null) return;
                    onerenderer.swipe(numTouch>1, fromEdge(tx,ty), (int)tx,screenHeight-(int)ty, dx, dy);
                }});
                break;
            default:
                tx=0; ty=0;
                px=0; py=0;
                numTouch=0;
                break;
        }
        triggerRenderingBurst();
        return true;
    }

    private int fromEdge(float tx,float ty){
        int borderWidth=40;
        if(ty<borderWidth*2)            return 1; // top strip
        if(ty>screenHeight-borderWidth) return 2; // bottom strip
        if(tx<borderWidth)              return 3; // left strip
        if(tx>screenWidth -borderWidth) return 4; // right strip
        return 0;
    }

    private void triggerRenderingBurst(){
        onemeshview.requestRender();
        onemeshview.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        new Thread(){ public void run(){ try{
            Kernel.sleep(2000);
            onemeshview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }catch(Throwable t){}}}.start();
    }

    //---------------------------------------------------------

    private RelativeLayout layout;
    private float          screenDensity;
    private int            screenWidth;
    private int            screenHeight;

    public void drawInitialView(){

        screenDensity = getResources().getDisplayMetrics().density;
        screenWidth   = getResources().getDisplayMetrics().widthPixels;
        screenHeight  = getResources().getDisplayMetrics().heightPixels;

        setContentView(R.layout.main);
        layout = (RelativeLayout)findViewById(R.id.Layout);
        layout.setBackgroundColor(0xffffffdd);
    }

    //---------------------------------------------------------

    private JSON uiJSON;
    private String uiUID;
    private Handler guiHandler = new Handler();
    private Runnable uiDrawJSONRunnable=new Runnable(){public void run(){uiDrawJSON();}};
    private boolean focused = false;
    private String viewUID = null;

    public void drawJSON(JSON uiJSON, String uiUID){ log("drawJSON "+uiUID);
        this.uiJSON=uiJSON;
        this.uiUID=uiUID;
        guiHandler.post(uiDrawJSONRunnable);
    }

    private void uiDrawJSON(){ if(false) log("uiDrawJSON "+uiUID+":\n"+uiJSON);
     // if(focused && viewUID.equals(uiUID)){ log("** locked"); return; }
        focused=false; viewUID=uiUID;
        String title =uiJSON.stringPathN("title");
        if(title==null) setTitle(       "NetMash");
        else            setTitle(title+"|NetMash");
        if("gui".equals(uiJSON.stringPathN("is"))){
            Object      o=uiJSON.hashPathN("view");
            if(o==null) o=uiJSON.listPathN("view");
            addGUI(o);
        }else{
            LinkedHashMap mesh=uiJSON.hashPathN("#");
            addMesh(mesh);
        }
    }

    public GLSurfaceView onemeshview=null;
    public Renderer      onerenderer=null;

    private void addMesh(LinkedHashMap mesh){
        if(createMeshView(mesh)){
            View v=layout.getChildAt(0);
            if(v!=null) layout.removeView(v);
            layout.addView(onemeshview, 0);
        }
    }

    private void addGUI(Object o){
        disposeOfMeshView();
        ViewGroup view;
        if(isMapList(o)){
            view = createMapView(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0,5,5,0);
            view.setLayoutParams(lp);
        }
        else
        if(isHorizontal(o)){
            view=createHorizontalStrip(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0,5,5,0);
            view.setLayoutParams(lp);
        }
        else{
            view=createVerticalStrip(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
            lp.setMargins(0,5,5,0);
            view.setLayoutParams(lp);
        }
        View v=layout.getChildAt(0);
        if(v!=null) layout.removeView(v);
        layout.addView(view, 0);
    }

    static final public int MENU_ITEM_ADD = Menu.FIRST+0;
    static final public int MENU_ITEM_LNX = Menu.FIRST+1;
    static final public int MENU_ITEM_GUI = Menu.FIRST+2;
    static final public int MENU_ITEM_MAP = Menu.FIRST+3;
    static final public int MENU_ITEM_RAW = Menu.FIRST+4;

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_ADD, Menu.NONE, "+ Link");
        menu.add(1, MENU_ITEM_LNX, Menu.NONE, "Links");
        menu.add(2, MENU_ITEM_GUI, Menu.NONE, "Object");
        menu.add(3, MENU_ITEM_MAP, Menu.NONE, "On Map");
        menu.add(4, MENU_ITEM_RAW, Menu.NONE, "View/Edit");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        super.onOptionsItemSelected(item);
        return user.menuItem(item.getItemId());
    }

    private ViewGroup createVerticalStrip(Object o){
        if(o instanceof LinkedHashMap) return createVerticalStrip((LinkedHashMap<String,Object>)o);
        else                           return createVerticalStrip((LinkedList)o);
    }

    private ViewGroup createHorizontalStrip(Object o){
        if(o instanceof LinkedHashMap) return createHorizontalStrip((LinkedHashMap<String,Object>)o);
        else                           return createHorizontalStrip((LinkedList)o);
    }

    private ViewGroup createVerticalStrip(LinkedHashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, hm);
        ScrollView view = new ScrollView(this);
        view.setScrollbarFadingEnabled(false);
        view.setSmoothScrollingEnabled(true);
        view.addView(layout);
        return view;
    }

    private ViewGroup createHorizontalStrip(LinkedHashMap<String,Object> hm){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, hm);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.setScrollbarFadingEnabled(false);
        view.setSmoothScrollingEnabled(true);
        view.addView(layout);
        return view;
    }

    private ViewGroup createVerticalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, ll);
        ScrollView view = new ScrollView(this);
        view.setScrollbarFadingEnabled(false);
        view.setSmoothScrollingEnabled(true);
        view.addView(layout);
        return view;
    }

    private ViewGroup createHorizontalStrip(LinkedList ll){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0,0,0,0);
        fillStrip(layout, ll);
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.setScrollbarFadingEnabled(false);
        view.setSmoothScrollingEnabled(true);
        view.addView(layout);
        return view;
    }

    private void fillStrip(LinearLayout layout, LinkedHashMap<String,Object> hm){
        String[] colours=null;
        float dim0=0;
        float dimn=0;
        float height=0;
        float width=0;
        int i=0;
        for(String tag: hm.keySet()){ Object o=hm.get(tag);
            if(o instanceof LinkedHashMap && "style".equals(((LinkedHashMap)o).get("is"))){
                LinkedHashMap<String,String> style=(LinkedHashMap<String,String>)o;
                for(String styletag: style.keySet()){
                    if(styletag.equals("colours")) colours=style.get(styletag).split(",");
                    else
                    if(styletag.equals("height")) height=parseFirstInt(style.get(styletag));
                    else
                    if(styletag.equals("width")) width=parseFirstInt(style.get(styletag));
                    else
                    if(styletag.equals("proportions")) dim0=parseFirstInt(style.get(styletag));
                }
                continue;
            }
            if(tag.equals("render")) continue;
            if(tag.equals("options")) continue;
            i++;
        }
        if(dim0 >0 && i>1) dimn=(99-dim0)/(i-1);
        i=0;
        for(String tag: hm.keySet()){ Object o=hm.get(tag);
            if(o instanceof LinkedHashMap && "style".equals(((LinkedHashMap)o).get("is"))) continue;
            if(tag.equals("render")) continue;
            if(tag.equals("options")) continue;
            addToLayout(layout, tag, o, selectColour(colours,i), i==0? dim0: dimn, width, height);
            i++;
        }
    }

    private void fillStrip(LinearLayout layout, LinkedList ll){
        String[] colours=null;
        float dim0=0;
        float dimn=0;
        float height=0;
        float width=0;
        int i=0;
        for(Object o: ll){
            if(o instanceof LinkedHashMap && "style".equals(((LinkedHashMap)o).get("is"))){
                LinkedHashMap<String,String> style=(LinkedHashMap<String,String>)o;
                for(String styletag: style.keySet()){
                    if(styletag.equals("colours")) colours=style.get(styletag).split(",");
                    else
                    if(styletag.equals("height")) height=parseFirstInt(style.get(styletag));
                    else
                    if(styletag.equals("width")) width=parseFirstInt(style.get(styletag));
                    else
                    if(styletag.equals("proportions")) dim0=parseFirstInt(style.get(styletag));
                }
                continue;
            }
            if(o instanceof String){
                String s=(String)o;
                if(s.startsWith("render:")) continue;
                if(s.startsWith("options:")) continue;
            }
            i++;
        }
        if(dim0 >0 && i>1) dimn=(99-dim0)/(i-1);
        i=0;
        for(Object o: ll){
            if(o instanceof LinkedHashMap && "style".equals(((LinkedHashMap)o).get("is"))) continue;
            if(o instanceof String){
                String s=(String)o;
                if(s.startsWith("render:")) continue;
                if(s.startsWith("options:")) continue;
            }
            addToLayout(layout, null, o, selectColour(colours,i), i==0? dim0: dimn, height, width);
            i++;
        }
    }

    private void addToLayout(LinearLayout layout, String tag, Object o, int colour, float prop, float height, float width){
        if(o==null) return;
        if(isMapList(o)){
            addAView(layout, createMapView(o), prop, height, width);
        }
        else
        if(o instanceof LinkedHashMap){
            LinkedHashMap<String,Object> hm=(LinkedHashMap<String,Object>)o;
            if(isHorizontal(hm)) addHorizontalStrip(layout, createHorizontalStrip(hm), colour, prop, height, width);
            else                 addVerticalStrip(  layout, createVerticalStrip(hm), colour, prop, height, width);
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            if(ll.size()==0) return;
            if(isHorizontal(ll)) addHorizontalStrip(layout, createHorizontalStrip(ll), colour, prop, height, width);
            else                 addVerticalStrip(  layout, createVerticalStrip(ll), colour, prop, height, width);
        }
        else{
            String s=o.toString();
            boolean isUID       = s.startsWith("uid-") || (s.startsWith("http://") && s.endsWith(".json"));
            boolean isImage     = s.startsWith("http://") && ( s.endsWith(".jpg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".ico"));
            boolean isWebURL    = s.startsWith("http://");
            boolean isFormField = s.startsWith("?[") && s.endsWith("]?");
            View v= isUID?       createUIDView(s):
                   (isImage?     createImageView(s):
                   (isWebURL?    createWebLinkView(s):
                   (isFormField? createFormView(tag, s, colour):
                                 createTextView(s, colour))));
            addAView(layout, v, prop, height, width);
        }
    }

    private void addHorizontalStrip(LinearLayout layout, View view, int colour, float prop, float height, float width){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addVerticalStrip(LinearLayout layout, View view, int colour, float prop, float height, float width){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addAView(LinearLayout layout, View view, float prop, float height, float width){
        int w=(width>0?  (int)(width*14): (prop!=0? (int)((prop/101.0)*screenWidth+0.5): FILL_PARENT));
        int h=(height>0? (int)((height/101.0)*screenHeight+0.5): FILL_PARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private TextView createUIDView(final String uid){
        final TextView view=new BoxTextView(this,R.drawable.box);
        view.setOnClickListener(new OnClickListener(){ public void onClick(View v){
            view.setTextColor(0xffff9900);
            user.jumpToUID(uid);
        }});
        view.setText(" >>");
        view.setTextSize(34);
        view.setTextColor(0xff000000);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        return view;
    }

    private ImageView createImageView(String url){
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(3,3,3,3);
        eventuallySetImageUsingDecentApproach(view,url);
        return view;
    }

    private TextView createWebLinkView(final String url){
        final TextView view=new BoxTextView(this,R.drawable.box);
        view.setOnClickListener(new OnClickListener(){ public void onClick(View v){
            Intent browserIntent=new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }});
        view.setText(" >>");
        view.setTextSize(34);
        view.setTextColor(0xff000000);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        return view;
    }

    private View createFormView(String tag, String s, int colour){
        int b=s.indexOf("/");
        int e=s.lastIndexOf("/");
        if(b== -1 || e== -1 || b==e) return createTextView(s,colour);
        String   label=s.substring(2,b).trim();
        String[] types=s.substring(b+1,e).split(";");
        if(types.length==0) return createTextView(s,colour);
        View view=null;
        if(types[0].equals("string")){
            if(types.length==1) view=createFormTextView(tag, label);
            else
            if(types[1].charAt(0)=='|') view=createFormRadioView(  tag, label, types[1].split("\\|", -1));
            else                        view=createFormSpinnerView(tag, label, types[1].split("\\|", -1));
        }
        else
        if(types[0].equals("boolean")){
            if(types.length==1) view=createFormCheckView( tag, label);
            else                view=createFormToggleView(tag, label, types[1].split("\\|", -1));
        }
        else
        if(types[0].equals("integer")){
            if(types.length==1) view=createFormRatingView(tag, label, null);
            else                view=createFormRatingView(tag, label, types[1].split("\\|", -1));
        }
        if(view==null) return createTextView(s,colour);
        return view;
    }

    private View createFormTextView(final String tag, String label){
        EditText view=new EditText(this){
            protected void onFocusChanged(boolean f, int d, Rect p){
                super.onFocusChanged(f, d, p);
                focused=f;
            }
        };
        view.setOnKeyListener(new OnKeyListener(){
            public boolean onKey(View v, int keyCode, KeyEvent event){
                if(event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.KEYCODE_ENTER){
                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
                    user.setFormVal(viewUID, tag, ((EditText)v).getText().toString());
                    return true;
                }
                return false;
            }
        });
        view.setBackgroundDrawable(getResources().getDrawable(R.drawable.inputbox));
        String val=user.getFormStringVal(viewUID, tag);
        if(val!=null) view.setText(val);
        else          view.setText(label);
        view.selectAll();
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        return view;
    }

    private View createFormRadioView(final String tag, String label, String[] choices){
        String val=user.getFormStringVal(viewUID, tag);
        RadioGroup view = new RadioGroup(this);
        for(String choice: choices){
            if(choice.length()==0) continue;
            RadioButton v = new RadioButton(this);
            v.setOnClickListener(new OnClickListener(){
                public void onClick(View v){ user.setFormVal(viewUID, tag, ((RadioButton)v).getText().toString()); }
            });
            v.setText(choice);
            v.setTextSize(20);
            v.setTextColor(0xff000000);
            view.addView(v);
            if(choice.equals(val)) v.setChecked(true);
        }
        return view;
    }

    private View createFormSpinnerView(final String tag, String label, String[] choices){
        Spinner view = new Spinner(this);
        view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
                user.setFormVal(viewUID, tag, parent.getItemAtPosition(pos).toString());
            }
            public void onNothingSelected(AdapterView parent){}
        });
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        view.setAdapter(adapter);
        String val=user.getFormStringVal(viewUID, tag);
        if(val!=null) view.setSelection(indexOf(val, choices));
        view.setPrompt(label);
        return view;
    }

    private int indexOf(String needle, String[] haystack){
        for(int i=0; i<haystack.length; i++) if(haystack[i].equals(needle)) return i;
        return 0;
    }

    private View createFormCheckView(final String tag, String label){
        CheckBox view = new CheckBox(this);
        view.setOnClickListener(new OnClickListener(){
            public void onClick(View v){ user.setFormVal(viewUID, tag, ((CheckBox)v).isChecked()); }
        });
        boolean val=user.getFormBoolVal(viewUID, tag);
        view.setChecked(val);
        view.setText(label);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        return view;
    }

    private View createFormToggleView(final String tag, String label, String[] choices){
        if(choices.length!=2) return null;
        ToggleButton view = new ToggleButton(this);
        view.setOnClickListener(new OnClickListener(){
            public void onClick(View v){ user.setFormVal(viewUID, tag, ((ToggleButton)v).isChecked()); }
        });
        boolean val=user.getFormBoolVal(viewUID, tag);
        view.setChecked(val);
        view.setText(choices[val? 1:0]);
        view.setTextOff(choices[0]);
        view.setTextOn(choices[1]);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        return view;
    }

    private View createFormRatingView(final String tag, String label, String[] choices){
        RatingBar view = new RatingBar(this);
        view.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener(){
            public void onRatingChanged(RatingBar b, float r, boolean f){ user.setFormVal(viewUID, tag, (int)r); }
        });
        int numchoices=5;
        if(choices!=null && choices.length!=0){
            try{ numchoices=Integer.parseInt(choices[choices.length-1]); }catch(Exception e){}
        }
        int val=user.getFormIntVal(viewUID, tag);
        view.setStepSize(1.0f);
        view.setNumStars(numchoices);
        view.setRating((float)val);
        return view;
    }

    private TextView createTextView(String s, int colour){
        final TextView view;
        if(colour!=0){ view=new BorderedTextView(this, colour); }
        else{          view=new BoxTextView(this,R.drawable.box); }
        if(s.startsWith("![") && s.endsWith("]!")){
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            s=s.substring(2,s.length()-2);
        }
        if(s.startsWith("/[") && s.endsWith("]/")){
            view.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            s=s.substring(2,s.length()-2);
        }
        view.setText(s);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        return view;
    }

    private MapView mapview=null;
    private HashMap<String,NetMashMapOverlay> layers = new HashMap<String,NetMashMapOverlay>();

    private MapView createMapView(Object o){
        if(o instanceof LinkedHashMap) return createMapView((LinkedHashMap<String,Object>)o);
        else                           return createMapView((LinkedList)o);
    }

    private MapView createMapView(LinkedHashMap<String,Object> hm){
        return null;
    }

    private MapView createMapView(LinkedList ll){
        if(mapview==null){
            mapview = new MapView(this, "03Hoq1TEN3zbZ9y69dEoFX0Tc20g14mWm-hImbQ");
            mapview.setEnabled(true);
            mapview.setClickable(true);
            mapview.setBuiltInZoomControls(true);
            mapview.displayZoomControls(true);
        }
        Drawable drawable = getResources().getDrawable(R.drawable.mappinlogo);
        NetMashMapOverlay itemizedoverlay=null;
        for(Object o: ll){
            if((o instanceof String) && o.toString().startsWith("layerkey:")){
                itemizedoverlay = layers.get(o.toString());
                if(itemizedoverlay==null){
                    itemizedoverlay = new NetMashMapOverlay(drawable, this);
                    layers.put(o.toString(), itemizedoverlay);
                }
                break;
            }
            if((o instanceof LinkedHashMap)) break;
        }
        if(itemizedoverlay==null) itemizedoverlay = new NetMashMapOverlay(drawable, this);
        itemizedoverlay.clear();
        int minlat=Integer.MAX_VALUE, maxlat=Integer.MIN_VALUE;
        int minlon=Integer.MAX_VALUE, maxlon=Integer.MIN_VALUE;
        for(Object o: ll){
            if(!(o instanceof LinkedHashMap)) continue;
            LinkedHashMap point = (LinkedHashMap)o;
            String label=(String)point.get("label");
            String sublabel=(String)point.get("sublabel");
            LinkedHashMap<String,Double> location=(LinkedHashMap<String,Double>)point.get("location");
            if(location==null) continue;
            String jumpUID=(String)point.get("jump");
            int lat=(int)(location.get("lat")*1e6);
            int lon=(int)(location.get("lon")*1e6);
            minlat=Math.min(lat,minlat); maxlat=Math.max(lat,maxlat);
            minlon=Math.min(lon,minlon); maxlon=Math.max(lon,maxlon);
            NetMashMapOverlay.Item overlayitem = new NetMashMapOverlay.Item(new GeoPoint(lat,lon), label, sublabel, jumpUID);
            itemizedoverlay.addItem(overlayitem);
        }
        if(minlat!=Integer.MAX_VALUE){ // following fails for cluster over +-180' lon
            MapController mapcontrol = mapview.getController();
            mapcontrol.animateTo(new GeoPoint((maxlat+minlat)/2, (maxlon+minlon)/2));
            int minspan=40000;
            int latspan=(maxlat-minlat); if(latspan<minspan) latspan=minspan;
            int lonspan=(maxlon-minlon); if(lonspan<minspan) lonspan=minspan;
            mapcontrol.zoomToSpan(latspan, lonspan);
        }
        List overlays = mapview.getOverlays();
        if(!overlays.contains(itemizedoverlay)) overlays.add(itemizedoverlay);
        mapview.postInvalidate();
        return mapview;
    }

    private boolean createMeshView(LinkedHashMap mesh){
        boolean newview=(onemeshview==null);
        if(newview){
            onemeshview = new GLSurfaceView(this);
            onemeshview.setFocusable(true);
            onemeshview.setFocusableInTouchMode(true);
            onemeshview.setEGLContextClientVersion(2);
            onerenderer = new Renderer(this,mesh);
            onemeshview.setRenderer(onerenderer);
            onemeshview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        else{
            onerenderer.newMesh(mesh);
            triggerRenderingBurst();
        }
        return newview;
    }

    private void disposeOfMeshView(){
        onemeshview=null;
        onerenderer=null;
    }

    // ---------------------------------------------------------------------

    public void jumpToUID(String s){
        user.jumpToUID(s);
    }

    private boolean isHorizontal(Object o){
        if(o instanceof LinkedHashMap){
            LinkedHashMap hm=(LinkedHashMap)o;
            for(Object oo: hm.values()){
                if(oo instanceof LinkedHashMap &&
                   "style".equals(((LinkedHashMap)oo).get("is")) &&
                   "horizontal".equals(((LinkedHashMap)oo).get("direction"))) return true;
            }
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            for(Object oo: ll){
                if(oo instanceof LinkedHashMap &&
                   "style".equals(((LinkedHashMap)oo).get("is")) &&
                   "horizontal".equals(((LinkedHashMap)oo).get("direction"))) return true;
            }
        }
        return false;
    }

    private boolean isMapList(Object o){
        if(o instanceof LinkedHashMap){
            LinkedHashMap hm=(LinkedHashMap)o;
            return "map".equals(hm.get("render"));
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            for(Object oo: ll) if("render:map".equals(oo)) return true;
        }
        return false;
    }

    @Override
    protected boolean isRouteDisplayed(){
        return false;
    }

    // ---------------------------------------------------------------------

    private HashMap<String,Bitmap> imageCache = new HashMap<String,Bitmap>();

    public Bitmap getBitmap(final String url){
        Bitmap bm=imageCache.get(url);
        if(bm!=null) return bm;
        // figure out how to replace a texture
        bm=getPlaceHolderBitmap();
        imageCache.put(url, bm);
        new Thread(){ public void run(){ getImageBitmap(url); }}.start();
        return bm;
    }

    private void eventuallySetImageUsingDecentApproach(final ImageView view, final String url){
        Bitmap bm=imageCache.get(url);
        if(bm!=null){ view.setImageBitmap(bm); return; }
        // view doesn't refresh when placeholder replaced by actual
        // bm=getPlaceHolderBitmap();
        // imageCache.put(url, bm);
        // view.setImageBitmap(bm);
        new Thread(){ public void run(){
            final Bitmap bm2 = getImageBitmap(url);
            guiHandler.post(new Runnable(){ public void run(){ view.setImageBitmap(bm2); }});
        }}.start();
    }

    private Bitmap placeHolderBitmap;
    public Bitmap getPlaceHolderBitmap(){
        if(placeHolderBitmap==null){
            InputStream placeis=getResources().openRawResource(R.raw.placeholder);
            placeHolderBitmap=BitmapFactory.decodeStream(placeis);
            try{ placeis.close(); }catch(IOException e){}
        }
        return placeHolderBitmap;
    }

    public Drawable getPlaceHolderDrawable(){
        return getResources().getDrawable(R.raw.placeholder);
    }

    private Bitmap getImageBitmap(String url){
        Bitmap bm=null;
        InputStream is=null;
        BufferedInputStream bis=null;
        try{
            URLConnection conn = new URL(url).openConnection(); conn.connect();
            is = conn.getInputStream();
            bis = new BufferedInputStream(is, 8092);
            bm = BitmapFactory.decodeStream(bis);
            if(bm==null) throw new Exception("couldn't decode bitmap stream");
            imageCache.put(url,bm);
        }catch(Throwable t){ t.printStackTrace(); System.err.println("Couldn't load image at "+url+"\n"+t);
        }finally{ try{ is.close(); bis.close(); }catch(Throwable t){}}
        return bm;
    }
/* For scalable non-compressed images in res:
            BitmapFactory.Options bmfo=new BitmapFactory.Options();
            bmfo.inScaled = false;
            bmfo.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(bis, null, bmfo);
            .. GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0); ..
            bitmap.recycle();
   http://code.google.com/p/gdc2011-android-opengl/wiki/TalkTranscript
*/

    // ---------------------------------------------------------------------

    private static final Map<String,Integer> COLOURMAP;
    static{
        Map<String,Integer> m=new HashMap<String,Integer>();
        m.put("white",         0xffffffff); m.put("white*",         0xffffffff);
        m.put("lightgrey",     0xeeeeeeee); m.put("lightgrey*",     0xeeeeeeee);
        m.put("lightpink",     0xffffeeee); m.put("lightpink*",     0xffffeeee);
        m.put("lightgreen",    0xffeeffee); m.put("lightgreen*",    0xffeeffee);
        m.put("lightblue",     0xffeeeeff); m.put("lightblue*",     0xffeeeeff);
        m.put("lightyellow",   0xffffffee); m.put("lightyellow*",   0xffffffee);
        m.put("lightmauve",    0xffffeeff); m.put("lightmauve*",    0xffffeeff);
        m.put("lightturquoise",0xffeeffff); m.put("lightturquoise*",0xffeeffff);
        COLOURMAP=Collections.unmodifiableMap(m);
    }
    private int selectColour(String[] colours, int i){
        if(colours==null) return 0;
        if(colours.length==0) return 0;
        if(i>=colours.length){
            if(colours[colours.length-1].trim().endsWith("*")) i=colours.length-1;
            else return 0;
        }
        String colour = colours[i].trim();
        Integer c = COLOURMAP.get(colour);
        if(c==null) return 0;
        return c.intValue();
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

class BorderedTextView extends TextView {
    Paint paint = new Paint();
    Rect rect = new Rect();
    public BorderedTextView(Context context, int colour){
        super(context);
        setBackgroundColor(colour);
        setPadding(15,5,5,5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(0xff888877);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        getLocalVisibleRect(rect);
        rect.inset(1,1);
        canvas.drawRect(rect, paint);
        super.onDraw(canvas);
    }
}

class BoxTextView extends TextView{
    public BoxTextView(Context context, int drawable){ super(context); setBackgroundDrawable(getResources().getDrawable(drawable)); }
}

}

//---------------------------------------------------------

