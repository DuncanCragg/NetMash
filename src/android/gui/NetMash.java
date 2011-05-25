
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
import android.graphics.drawable.*;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import static android.view.ViewGroup.LayoutParams.*;

import com.google.android.maps.*;

import netmash.platform.Kernel;
import netmash.lib.JSON;
import netmash.forest.FunctionalObserver;

import android.User;

/**  NetMash main.
  */
public class NetMash extends MapActivity {

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
        if(user==null) User.createUserAndDevice();
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
    private float          screenDensity;
    private int            screenWidth;

    public void drawInitialView(){

        screenDensity = getResources().getDisplayMetrics().density;
        screenWidth   = getResources().getDisplayMetrics().widthPixels;

        setContentView(R.layout.main);
        layout = (RelativeLayout)findViewById(R.id.Layout);
        layout.setBackgroundColor(0xffffffdd);
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
        Object      o=uiJSON.hashPathN("view");
        if(o==null) o=uiJSON.listPathN("view");
        boolean horizontal=isHorizontal(o);
        View view;
        if(isMapList(o)){
            view = createMapView(o);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            lp.setMargins(0,5,5,0);
            view.setLayoutParams(lp);
        }
        else
        if(horizontal){
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ITEM_ADD, Menu.NONE, "+ Link");
        menu.add(1, MENU_ITEM_LNX, Menu.NONE, "Links");
        menu.add(2, MENU_ITEM_GUI, Menu.NONE, "Object");
        menu.add(3, MENU_ITEM_MAP, Menu.NONE, "On Map");
        menu.add(4, MENU_ITEM_RAW, Menu.NONE, "Raw JSON");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        super.onOptionsItemSelected(item);
        return user.menuItem(item.getItemId());
    }

    private View createVerticalStrip(Object o){
        if(o instanceof LinkedHashMap) return createVerticalStrip((LinkedHashMap<String,Object>)o);
        else                           return createVerticalStrip((LinkedList)o);
    }

    private View createHorizontalStrip(Object o){
        if(o instanceof LinkedHashMap) return createHorizontalStrip((LinkedHashMap<String,Object>)o);
        else                           return createHorizontalStrip((LinkedList)o);
    }

    private View createVerticalStrip(LinkedHashMap<String,Object> hm){
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

    private View createHorizontalStrip(LinkedHashMap<String,Object> hm){
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

    private View createVerticalStrip(LinkedList ll){
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

    private View createHorizontalStrip(LinkedList ll){
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
        float dim0=0;
        float dimn=0;
        int i=0;
        for(String tag: hm.keySet()){
            if(tag.equals("render")) continue;
            if(tag.equals("direction")) continue;
            if(tag.equals("options")) continue;
            if(tag.equals("colours")) continue;
            if(tag.equals("proportions")){
                dim0=parseFirstInt(hm.get(tag).toString());
                continue;
            }
            i++;
        }
        if(dim0 >0 && i>1) dimn=(99-dim0)/(i-1);
        i=0;
        for(String tag: hm.keySet()){
            if(tag.equals("render")) continue;
            if(tag.equals("direction")) continue;
            if(tag.equals("options")) continue;
            if(tag.equals("colours")) continue;
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
                String s=(String)o;
                if(s.startsWith("render:")) continue;
                if(s.startsWith("direction:")) continue;
                if(s.startsWith("options:")) continue;
                if(s.startsWith("proportions:")){
                    dim0=parseFirstInt(s);
                    continue;
                }
            }
            i++;
        }
        if(dim0 >0 && i>1) dimn=(99-dim0)/(i-1);
        i=0;
        for(Object o: ll){
            if(o instanceof String){
                String s=(String)o;
                if(s.startsWith("render:")) continue;
                if(s.startsWith("direction:")) continue;
                if(s.startsWith("options:")) continue;
                if(s.startsWith("proportions:")) continue;
            }
            addToLayout(layout, o, i==0? dim0: dimn);
            i++;
        }
    }

    private void addToLayout(LinearLayout layout, Object o, float prop){
        if(o==null) return;
        if(isMapList(o)){
            addAView(layout, createMapView(o), prop);
        }
        else
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
            boolean isUID   = s.startsWith("uid-") || (s.startsWith("http://") && s.indexOf("uid-")!= -1 && s.endsWith(".json"));
            boolean isImage = s.startsWith("http://") && s.endsWith(".jpg");
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
        final TextView view=new TextView(this);
        view.setText(" >>");
        view.setOnClickListener(new OnClickListener(){ public void onClick(View v){
            view.setTextColor(0xffff9900);
            user.jumpToUID(s);
        }});
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setTextSize(34);
        view.setBackgroundDrawable(getResources().getDrawable(R.drawable.box));
        view.setTextColor(0xff000000);
        return view;
    }

    private TextView createTextView(String s){
        TextView view=new TextView(this);
        view.setText(s);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setTextSize(20);
        view.setBackgroundDrawable(getResources().getDrawable(R.drawable.box));
        view.setTextColor(0xff000000);
        return view;
    }

    private ImageView createImageView(String url){
        ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setPadding(8,8,8,8);
        view.setImageBitmap(getImageBitmap(url));
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
            mapview = new MapView(this, "03Hoq1TEN3zbEGUSHYbrBqYgXhph-qRQ7g8s3UA");
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
            mapcontrol.zoomToSpan(             maxlat-minlat,     maxlon-minlon);
        }
        List overlays = mapview.getOverlays();
        if(!overlays.contains(itemizedoverlay)) overlays.add(itemizedoverlay);
        mapview.postInvalidate();
        return mapview;
    }

    public void jumpToUID(String s){
        user.jumpToUID(s);
    }

    private boolean isHorizontal(Object o){
        if(o instanceof LinkedHashMap){
            LinkedHashMap<String,Object> hm=(LinkedHashMap<String,Object>)o;
            return "horizontal".equals(hm.get("direction"));
        }
        else{
            LinkedList ll=(LinkedList)o;
            return "direction:horizontal".equals(ll.get(0));
        }
    }

    private boolean isMapList(Object o){
        if(o instanceof LinkedHashMap){
            LinkedHashMap<String,Object> hm=(LinkedHashMap<String,Object>)o;
            return "map".equals(hm.get("render"));
        }
        else
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            return !ll.isEmpty() && "render:map".equals(ll.get(0).toString());
        }
        return false;
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
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

