
package cyrus.gui;

import java.util.*;

import android.os.*;
import android.net.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import com.google.android.maps.*;

import static cyrus.lib.Utils.*;

public class CyrusMapOverlay extends ItemizedOverlay implements DialogInterface.OnClickListener {

    private String mapUID=null;
    private String jumpUID;

    static public class Item extends OverlayItem{
        String jumpUID;
        public Item(GeoPoint point, String label, String sublabel, String jumpUID){
            super(point, label, sublabel);
            this.jumpUID=jumpUID;
        }
    }

    private ArrayList<Item> overlayitems = new ArrayList<Item>();
    private NetMash cyrus;

    public CyrusMapOverlay(Drawable defaultMarker, NetMash cyrus, String mapUID){
        super(boundCenterBottom(defaultMarker));
        populate();
        this.cyrus = cyrus;
        this.mapUID = mapUID;
    }

    public void addItem(Item item){
        overlayitems.add(item);
        populate();
    }

    public void clear(){
        overlayitems.clear();
        populate();
    }

    @Override
    protected boolean onTap(int index){
        Item item = overlayitems.get(index);
        jumpUID=item.jumpUID;
        AlertDialog.Builder dialog = new AlertDialog.Builder(cyrus);
        dialog.setTitle(item.getTitle());
        dialog.setIcon(cyrus.getResources().getDrawable(R.drawable.mappinlogo));
        String[] choices = new String[3];
        choices[0]=item.getSnippet().replace("\n",", ");
        choices[1]="Jump here";
        choices[2]="View item";
        dialog.setItems(choices, this);
        dialog.show();
        return true;
    }

    private boolean multimove=false;

    @Override
    public boolean onTap(GeoPoint p, MapView map){
        if(super.onTap(p,map)) return true;
        if(multimove) return false;
        if(p!=null){ if(mapUID!=null) cyrus.user.setUpdateVal(mapUID, p); return true; }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e, MapView mapView){
        if(e.getAction()==MotionEvent.ACTION_DOWN) multimove=false;
        else
        if(e.getAction()==MotionEvent.ACTION_MOVE && e.getPointerCount()==2) multimove=true;
        return super.onTouchEvent(e, mapView);
    }

    @Override
    public void onClick(DialogInterface dialog, int choice){
        if(choice==1) cyrus.user.jumpToUID(jumpUID);
        if(choice==2) cyrus.user.jumpToUID(jumpUID,"gui",false);
    }

    @Override
    protected Item createItem(int i){ return overlayitems.get(i); }

    @Override
    public int size(){ return overlayitems.size(); }

    @Override
    public void draw(Canvas canvas, MapView mapview, boolean shadow){
        cyrus.saveZoom(mapview.getZoomLevel());
        super.draw(canvas, mapview, shadow);
    }
}


