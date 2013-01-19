
package cyrus.gui;

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
import android.text.InputType;

import android.opengl.GLSurfaceView;

import static android.view.ViewGroup.LayoutParams.*;

import com.google.android.maps.*;

import cyrus.platform.Kernel;
import cyrus.lib.*;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

import cyrus.User;

/**  Cyrus main.
  */
public class Cyrus extends MapActivity{

    static public Cyrus top=null;
    static public User  user=null;

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
        disposeOfMeshView();
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
        disposeOfMeshView();
    }

    @Override
    public void onDestroy(){
        super.onDestroy(); log("onDestroy");
        disposeOfMeshView();
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
                    InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
                    return true;
                }
                return false;
            }
        });
*/
    }

    private float ox=0f;
    private float oy=0f;
    private float px=0f;
    private float py=0f;
    private float tx=0f;
    private float ty=0f;
    private float qx=0f;
    private float qy=0f;
    private int   numTouch=0;

    @Override
    public boolean onTouchEvent(MotionEvent e){
        if(onemeshview==null) return false;
        final float xx,yy;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                ox=e.getX(0); oy=e.getY(0);
                px=ox; py=oy;
                numTouch=1;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                tx=e.getX(1); ty=e.getY(1);
                qx=tx; qy=ty;
                numTouch=2;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                xx=tx; yy=ty;
                if(qx==tx && qy==ty) onemeshview.queueEvent(new Runnable(){ public void run(){
                    if(onerenderer==null) return;
                    onerenderer.swipe(true, 0, (int)xx,screenHeight-(int)yy, 0,0);
                }});
                numTouch=1;
                break;
            case MotionEvent.ACTION_UP:
                xx=ox; yy=oy;
                if(px==ox && py==oy) onemeshview.queueEvent(new Runnable(){ public void run(){
                    if(onerenderer==null) return;
                    onerenderer.swipe(false, 0, (int)xx,screenHeight-(int)yy, 0,0);
                }});
                ox=0; oy=0;
                px=0; py=0;
                tx=0; ty=0;
                qx=0; qy=0;
                numTouch=0;
                break;
            case MotionEvent.ACTION_MOVE:
                if(numTouch==0) break;
                float cx=0,cy=0;
                if(numTouch==1){ cx=e.getX(0); cy=e.getY(0); }
                if(numTouch==2){ cx=e.getX(1); cy=e.getY(1); }
                final boolean mt=numTouch>1;
                float mx,my;
                if(!mt){
                    mx=cx-px; my=cy-py;
                    if(mx*mx+my*my<0.1) return true;
                    px=cx; py=cy;
                    xx=ox; yy=oy;
                }else{
                    mx=cx-qx; my=cy-qy;
                    if(mx*mx+my*my<0.1) return true;
                    qx=cx; qy=cy;
                    xx=tx; yy=ty;
                }
                final float dx=100*mx/screenWidth;
                final float dy=100*my/screenHeight;
                onemeshview.queueEvent(new Runnable(){ public void run(){
                    if(onerenderer==null) return;
                    onerenderer.swipe(mt, fromEdge(xx,yy), (int)xx,screenHeight-(int)yy, dx,dy);
                }});
                break;
            default:
                ox=0; oy=0;
                px=0; py=0;
                tx=0; ty=0;
                qx=0; qy=0;
                numTouch=0;
                break;
        }
        triggerRenderingBurst();
        return true;
    }

    private int fromEdge(float tx,float ty){
        float borderPercent=15.0f/100.0f;
        float xBorderWidth=screenWidth *borderPercent;
        float yBorderWidth=screenHeight*borderPercent;
        if(ty<yBorderWidth+20)           return 1; // top strip
        if(ty>screenHeight-yBorderWidth) return 2; // bottom strip
        if(tx<xBorderWidth)              return 3; // left strip
        if(tx>screenWidth -xBorderWidth) return 4; // right strip
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
        layout.setBackgroundColor(0xffffffee);
    }

    //---------------------------------------------------------

    private JSON uiJSON;
    private String uiUID;
    private Handler guiHandler = new Handler();
    private Runnable uiDrawJSONRunnable=new Runnable(){public void run(){uiDrawJSON();}};
    private String viewUID = null;

    public void drawJSON(JSON uiJSON, String uiUID){ if(false) log("drawJSON "+uiUID);
        this.uiJSON=uiJSON;
        this.uiUID=uiUID;
        guiHandler.post(uiDrawJSONRunnable);
    }

    private void uiDrawJSON(){ if(false) log("uiDrawJSON "+uiUID+":\n"+uiJSON);
        viewUID=uiUID;
        String title =uiJSON.stringPathN("title");
        if(title==null) setTitle(       "Cyrus");
        else            setTitle(title+"|Cyrus");
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
            lp.setMargins(0,0,0,0);
            view.setLayoutParams(lp);
        }
        else
        if(isHorizontal(o)){
            view=createHorizontalStrip(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0,0,0,0);
            view.setLayoutParams(lp);
        }
        else{
            view=createVerticalStrip(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
            lp.setMargins(0,0,0,0);
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
        menu.add(2, MENU_ITEM_GUI, Menu.NONE, "Item");
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
        view.setScrollbarFadingEnabled(true);
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
        view.setScrollbarFadingEnabled(true);
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
        boolean borderless=false;
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
                    else
                    if(styletag.equals("borders")) borderless=style.get(styletag).equals("none");
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
            addToLayout(layout, tag, o, selectColour(colours,i), borderless, i==0? dim0: dimn, width, height);
            i++;
        }
    }

    private void fillStrip(LinearLayout layout, LinkedList ll){
        String[] colours=null;
        float dim0=0;
        float dimn=0;
        float height=0;
        float width=0;
        boolean borderless=false;
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
                    else
                    if(styletag.equals("borders")) borderless=style.get(styletag).equals("none");
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
            addToLayout(layout, "fixed", o, selectColour(colours,i), borderless, i==0? dim0: dimn, height, width);
            i++;
        }
    }

    private void addToLayout(LinearLayout layout, String tag, Object o, int colour, boolean borderless, float prop, float height, float width){
        if(o==null) return;
        if(isMapList(o)){
            addAView(layout, createMapView(o), prop, height, width);
        }
        else
        if(o instanceof LinkedHashMap){
            LinkedHashMap<String,Object> hm=(LinkedHashMap<String,Object>)o;
            String type=getStringFrom(hm,"input");
            boolean isFormField = type!=null;
            if(isFormField){
                boolean needsLabel = hm.get("label")!=null && ("checkbox".equals(type)||"textfield".equals(type)||"rating".equals(type));
                if(needsLabel){  LinkedHashMap widget=(LinkedHashMap)hm.clone();
                                 Object label=widget.remove("label");
                                 if(!"rating".equals(type)){
                                     String proportions="checkbox".equals(type)? "85%":"40%";
                                     LinkedHashMap hs=hash("style",style("direction","horizontal","proportions",proportions), "label",label, tag,widget);
                                     addStrip(layout, createHorizontalStrip(hs), colour, prop, height, width);
                                 }
                                 else{
                                     LinkedHashMap hs=hash("style",style("direction","vertical"), "label",label, tag,widget);
                                     addStrip(layout, createVerticalStrip(hs), colour, prop, height, width);
                                 }
                }
                else  addAView(layout, createFormView(tag, hm, colour, borderless), prop, height, width);
            }
            else
            if(isHorizontal(hm)) addStrip(layout, createHorizontalStrip(hm), colour, prop, height, width);
            else                 addStrip(layout, createVerticalStrip(hm),   colour, prop, height, width);
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            if(ll.size()==0) return;
            if(isHorizontal(ll)) addStrip(layout, createHorizontalStrip(ll), colour, prop, height, width);
            else                 addStrip(layout, createVerticalStrip(ll),   colour, prop, height, width);
        }
        else{
            String s=o.toString();
            boolean isUID       = s.startsWith("uid-") || (s.startsWith("http://") && (s.endsWith(".json")||s.endsWith(".cyr")));
            boolean isImage     = s.startsWith("http://") && ( s.endsWith(".jpg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".ico"));
            boolean isWebURL    = s.startsWith("http://");
            View v= isUID?       createUIDView(s):
                   (isImage?     createImageView(s):
                   (isWebURL?    createWebLinkView(s):
                                 createTextView(s, colour, borderless)));
            addAView(layout, v, prop, height, width);
        }
    }

    private void addStrip(LinearLayout layout, View view, int colour, float prop, float height, float width){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(FILL_PARENT, WRAP_CONTENT);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private void addAView(LinearLayout layout, View view, float prop, float height, float width){
        int w=(width>0?  (int)(width*14): (prop!=0? (int)((prop/101.0)*screenWidth+0.5): FILL_PARENT));
        int h=(height>0? (int)((height/101.0)*screenHeight+0.5): FILL_PARENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(view instanceof RatingBar? WRAP_CONTENT: w, h);
        lp.setMargins(0,0,0,0);
        view.setLayoutParams(lp);
        layout.addView(view);
    }

    private TextView createUIDView(final String uid){
        final TextView view=new BoxTextView(this,R.drawable.box);
        view.setOnClickListener(new OnClickListener(){ public void onClick(View v){
            view.setTextColor(0xffff9900);
            user.jumpToUID(uid,null,true);
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

    private View createFormView(String tag, LinkedHashMap hm, int colour, boolean borderless){
        String  type  =getStringFrom( hm,    "input");
        String  label =getStringFrom( hm,    "label");
        Object  range =               hm.get("range");
        Object  value =               hm.get("value");
        boolean fixed =getBooleanFrom(hm,    "fixed") || "fixed".equals(tag);
        boolean scroll=getBooleanFrom(hm,    "scroll");
        View view=null;
        if("button".equals(type))    view=createFormButtonView(tag, label);
        else
        if("checkbox".equals(type))  view=createFormCheckView(tag, value);
        else
        if("textfield".equals(type)) view=createFormTextView(tag, value, borderless, fixed, scroll);
        else
        if("chooser".equals(type))   view=createFormSpinnerView(tag, label, range, value);
        else
        if("rating".equals(type))    view=createFormRatingView(tag, label, value, fixed);
        else
                                     view=createTextView(label+": "+value,colour,borderless);
        return view;
     //         view=createFormRadioView( tag, label, types[1].split("\\|", -1));
     //         view=createFormToggleView(tag, label, types[1].split("\\|", -1));
     //         view=createFormRatingView(tag, label, types[1].split("\\|", -1));
    }

    private View createFormButtonView(final String tag, String label){
        Button view=new Button(this);
        view.setOnClickListener(new OnClickListener(){
            public void onClick(View v){ user.setUpdateVal(viewUID, tag, true); }
        });
        user.prepareResponse(viewUID);
        view.setText(label);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        return view;
    }

    private View createFormTextView(final String tag, Object value, boolean borderless, final boolean fixed, boolean scroll){
        final String text=(value!=null)? value.toString(): "";
        EditText view=new EditText(this);
        if(!borderless) view.setBackgroundDrawable(getResources().getDrawable(R.drawable.inputbox));
        else            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.borderlessinputbox));
        if(fixed)       view.setBackgroundColor(0xffffffee);
        view.setOnKeyListener(  new OnKeyListener(){   public boolean onKey(  View v, int k, KeyEvent ev){ return updateOnEnter(v,k,ev,tag,fixed?text:null); }});
        view.setOnTouchListener(new OnTouchListener(){ public boolean onTouch(View v, MotionEvent ev){     return jumpIfUID(v,ev); }});
        String v1=user.getFormStringVal(viewUID,tag);
        view.setText(v1!=null? v1: text);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        view.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setSingleLine(false);
        view.setMinLines(scroll? 1: 2);
        view.setHorizontalScrollBarEnabled(scroll);
        view.setHorizontallyScrolling(scroll);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textViewForRaw=view;
        return view;
    }

    private TextView textViewForRaw;

    public String getRawSource(){ return textViewForRaw.getText().toString(); }

    private boolean updateOnEnter(View v, int keyCode, KeyEvent event, String tag, String revert){
        EditText view=(EditText)v;
        String currentText=view.getText().toString();
        if(event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.KEYCODE_ENTER){
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
            if(revert==null) user.setUpdateVal(viewUID, tag, currentText);
            else view.setText(revert);
            return true;
        }
        return false;
    }

    private float jx= -1;

    private boolean jumpIfUID(View v, MotionEvent ev){
        if(ev.getAction()!=MotionEvent.ACTION_MOVE){ jx= -1; return false; }
        float x=ev.getX(0);
        if(jx== -1){ jx=x; return false; }
        float dx=x-jx;
        jx= -1;
        if(dx>0) return false;
        EditText view=(EditText)v;
        int s=view.getSelectionStart();
        int e=view.getSelectionEnd();
        if(s!=e) return false;
        String currentText=view.getText().toString();
        String uid;
        do{ if(--s<=0) return false;
            uid=currentText.substring(s,e);
        }while(!uid.startsWith(" "));
        s++;
        while(++e< currentText.length()){
            uid=currentText.substring(s,e);
            if(uid.endsWith(" ") || uid.endsWith("\n")){
                if(uid.startsWith("uid-")||uid.startsWith("http://")){
                    user.jumpToUID(uid.trim(),null,true);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private View createFormRadioView(final String tag, String[] choices){
        user.prepareResponse(viewUID);
        RadioGroup view = new RadioGroup(this);
        for(String choice: choices){
            if(choice.length()==0) continue;
            RadioButton v = new RadioButton(this);
            v.setOnClickListener(new OnClickListener(){
                public void onClick(View v){ user.setUpdateVal(viewUID, tag, ((RadioButton)v).getText().toString()); }
            });
            v.setText(choice);
            v.setTextSize(20);
            v.setTextColor(0xff000000);
         // v.setChecked(false);
            view.addView(v);
        }
        return view;
    }

    private View createFormSpinnerView(final String tag, final String label, Object choices, Object value){
        if(choices==null || !(choices instanceof LinkedHashMap)) return createTextView(label+": "+value,0,false);
        LinkedHashMap<String,String> choiceshash=(LinkedHashMap<String,String>)choices;
        final LinkedList<String> valueslist=new LinkedList<String>();
        final LinkedList<String> choiceslist=new LinkedList<String>();
        if(value==null){
            valueslist.add("-label-");
            choiceslist.add(label);
        }
        for(Map.Entry<String,String> entry: choiceshash.entrySet()){
            valueslist.add(entry.getKey());
            choiceslist.add(entry.getValue());
        }
        String[] choicesarray=choiceslist.toArray(new String[0]);
        Spinner view = new Spinner(this);
        view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
                String val=valueslist.get(pos);
                if(!val.equals("-label-")) user.setUpdateVal(viewUID, tag, val);
            }
            public void onNothingSelected(AdapterView parent){}
        });
        user.prepareResponse(viewUID);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, choicesarray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        view.setAdapter(adapter);
        if(value!=null){ int i=0; for(String s: valueslist){ if(value.equals(s)){ view.setSelection(i); break; } i++; }}
        view.setPrompt(label);
        return view;
    }

    private int indexOf(String needle, String[] haystack){
        for(int i=0; i<haystack.length; i++) if(haystack[i].equals(needle)) return i;
        return 0;
    }

    private View createFormCheckView(final String tag, Object value){
        CheckBox view = new CheckBox(this);
        view.setOnClickListener(new OnClickListener(){
            public void onClick(View v){ user.setUpdateVal(viewUID, tag, ((CheckBox)v).isChecked()); }
        });
        Boolean v1=user.getFormBooleanVal(viewUID,tag);
        Boolean v2=value!=null && (value instanceof Boolean) && ((Boolean)value);
        view.setChecked(v1!=null? v1: (v2!=null? v2: false));
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        return view;
    }

    private View createFormToggleView(final String tag, String label, String[] choices){
        if(choices.length!=2) return null;
        ToggleButton view = new ToggleButton(this);
        view.setOnClickListener(new OnClickListener(){
            public void onClick(View v){ user.setUpdateVal(viewUID, tag, ((ToggleButton)v).isChecked()); }
        });
        boolean on="true".equals(label);
        user.prepareResponse(viewUID);
        view.setChecked(on);
        view.setText(choices[on? 1:0]);
        view.setTextOff(choices[0]);
        view.setTextOn(choices[1]);
        view.setTextSize(20);
        view.setTextColor(0xff000000);
        return view;
    }

    private View createFormRatingView(final String tag, String label, Object value, final boolean fixed){
        final Double v2=(value!=null && (value instanceof Double))? ((Double)value): null;
        RatingBar view = new RatingBar(this);
        view.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener(){ public void onRatingChanged(RatingBar b, float r, boolean f){
            if(!f) return;
            if(fixed) b.setRating((float)(v2!=null? v2: 0));
            else user.setUpdateVal(viewUID,tag,(int)(r+0.5));
        }});
        user.prepareResponse(viewUID);
        view.setStepSize(1.0f);
        view.setNumStars(5);
        Double v1=fixed? null: user.getFormDoubleVal(viewUID,tag);
        view.setRating((float)(v1!=null? v1: (v2!=null? v2: 0)));
        return view;
    }

    private TextView createTextView(String s, int colour, boolean borderless){
        final TextView view;
        if(colour!=0){ if(borderless) view=new BorderlessTextView(this, colour);
                       else           view=new BorderedTextView(this, colour);
        } else                        view=new BoxTextView(this,R.drawable.box);
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
    private HashMap<String,CyrusMapOverlay> layers = new HashMap<String,CyrusMapOverlay>();

    private MapView createMapView(Object o){
        if(o instanceof LinkedHashMap) return createMapView((LinkedHashMap<String,Object>)o);
        else                           return createMapView((LinkedList)o);
    }

    private MapView createMapView(LinkedHashMap<String,Object> hm){
        return null;
    }

    private static int MINSPAN=4000;

    private MapView createMapView(LinkedList ll){
        if(mapview==null){
            mapview = new MapView(this, "03Hoq1TEN3zaDOQmSJNHwHM5fRQ3dajOdQYZGbw");
            mapview.setEnabled(true);
            mapview.setClickable(true);
            mapview.setBuiltInZoomControls(true);
            mapview.displayZoomControls(true);
            mapview.getZoomButtonsController().setAutoDismissed(false);
        }
        mapview.setSatellite(false);
        Drawable drawable = getResources().getDrawable(R.drawable.mappinlogo);
        CyrusMapOverlay itemizedoverlay=null;
        boolean updatable=false;
        for(Object o: ll){
            if(!(o instanceof String)) break;
            String s=(String)o;
            if(s.equals("updatable")){
                updatable=true;
                continue;
            }
            if(s.equals("satellite")){
                mapview.setSatellite(true);
                continue;
            }
            if(s.startsWith("layerkey:")){
                itemizedoverlay = layers.get(s);
                if(itemizedoverlay==null){
                    itemizedoverlay = new CyrusMapOverlay(drawable, this, updatable? viewUID: null);
                    layers.put(s, itemizedoverlay);
                }
                continue;
            }
        }
        if(itemizedoverlay==null) itemizedoverlay = new CyrusMapOverlay(drawable, this, updatable? viewUID: null);
        PolygonOverlay polygonoverlay=new PolygonOverlay();
        itemizedoverlay.clear();
        int minlat=Integer.MAX_VALUE, maxlat=Integer.MIN_VALUE;
        int minlon=Integer.MAX_VALUE, maxlon=Integer.MIN_VALUE;
        for(Object o: ll){
            if(!(o instanceof LinkedHashMap)) continue;
            LinkedHashMap point = (LinkedHashMap)o;
            String label=(String)point.get("label");
            String sublabel=(String)point.get("sublabel");
            LinkedHashMap<String,Number> location=(LinkedHashMap<String,Number>)point.get("location");
            if(location==null) continue;
            List<GeoPoint> shape=listLocation2listPoints((LinkedList)point.get("shape"));
            String jumpUID=(String)point.get("jump");
            int lat=(int)(location.get("lat").doubleValue()*1e6);
            int lon=(int)(location.get("lon").doubleValue()*1e6);
            minlat=Math.min(lat,minlat); maxlat=Math.max(lat,maxlat);
            minlon=Math.min(lon,minlon); maxlon=Math.max(lon,maxlon);
            CyrusMapOverlay.Item overlayitem = new CyrusMapOverlay.Item(new GeoPoint(lat,lon), label, sublabel, jumpUID);
            itemizedoverlay.addItem(overlayitem);
            if(shape==null) continue;
            PolygonOverlay.PolyItem polyitem = new PolygonOverlay.PolyItem(shape, getPolyPaint());
            polygonoverlay.addItem(polyitem);
        }
        if(minlat!=Integer.MAX_VALUE){
            Projection projection = mapview.getProjection();
            GeoPoint middle=new GeoPoint((maxlat+minlat)/2, (maxlon+minlon)/2);
            GeoPoint toplef=new GeoPoint(maxlat,minlon);
            GeoPoint botrit=new GeoPoint(minlat,maxlon);
            Point middlep = new Point(); projection.toPixels(middle,middlep);
            Point toplefp = new Point(); projection.toPixels(toplef,toplefp);
            Point botritp = new Point(); projection.toPixels(botrit,botritp);
            MapController mapcontrol = mapview.getController();
            if(offScreen(middlep)){
                mapcontrol.animateTo(middle);
            }
            if(zoomlevel== -1 || offScreen(toplefp) || offScreen(botritp)){
                // following fails for cluster over +-180' lon
                int latspan=(maxlat-minlat); if(latspan<MINSPAN) latspan=MINSPAN;
                int lonspan=(maxlon-minlon); if(lonspan<MINSPAN) lonspan=MINSPAN;
                mapcontrol.animateTo(middle);
                mapcontrol.zoomToSpan(latspan, lonspan);
            }
        }
        List overlays = mapview.getOverlays();
    //  overlays.remove(itemizedoverlay);
        overlays.clear();
        overlays.add(itemizedoverlay);
        overlays.add(polygonoverlay);
        mapview.postInvalidate();
        saveZoom(mapview.getZoomLevel());
        return mapview;
    }

    boolean offScreen(Point p){ return p.x<=0 || p.y<=0 || p.x>=screenWidth || p.y>=screenHeight; }

    int zoomlevel= -1;
    void saveZoom(int zoom){
        zoomlevel=zoom;
    }

    Paint paint=null;

    private Paint getPolyPaint(){
        if(paint!=null) return paint;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setAlpha(50);
        paint.setDither(true);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3);
        return paint;
    }

    private List<GeoPoint> listLocation2listPoints(LinkedList locations){
        if(locations==null) return null;
        List<GeoPoint> listPoints = new ArrayList<GeoPoint>();
        for(Object o: locations){
            LinkedHashMap<String,Number> location=(LinkedHashMap<String,Number>)o;
            int lat=(int)(location.get("lat").doubleValue()*1e6);
            int lon=(int)(location.get("lon").doubleValue()*1e6);
            listPoints.add(new GeoPoint(lat,lon));
        }
        return listPoints;
    }

    private boolean createMeshView(LinkedHashMap mesh){
        boolean newview=(onemeshview==null);
        if(newview){
            onemeshview = new GLSurfaceView(this);
            onemeshview.setFocusable(true);
            onemeshview.setFocusableInTouchMode(true);
            onemeshview.setEGLContextClientVersion(2);
            onerenderer = new Renderer(this,mesh,user.getPosition(viewUID));
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
        if(onemeshview!=null) onemeshview.onPause();
        onemeshview=null;
        onerenderer=null;
    }

    // ---------------------------------------------------------------------

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
        m.put("androidorange", 0xffff9900); m.put("androidorange*", 0xffff9900);
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

    public void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    //---------------------------------------------------------

    private void runKernel(){

        InputStream configis = getResources().openRawResource(R.raw.cyrusconfig);
        JSON config=null;
        try{ config = new JSON(configis,true); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        String db = config.stringPathN("persist:db");

        InputStream topdbis=null;
        try{ topdbis = openFileInput(db); }catch(Exception e){ }
        if(topdbis==null) topdbis = getResources().openRawResource(R.raw.top);

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

class BorderlessTextView extends TextView {
    public BorderlessTextView(Context context, int colour){
        super(context);
        setBackgroundColor(colour);
        setPadding(0,0,0,0);
    }
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
    public BoxTextView(Context context, int drawable){
        super(context);
        setBackgroundDrawable(getResources().getDrawable(drawable));
    }
}

}

//---------------------------------------------------------

