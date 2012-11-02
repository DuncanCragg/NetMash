package android.gui;

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

import static netmash.lib.Utils.*;

public class PolygonOverlay extends Overlay {

    static public class PolyItem extends OverlayItem {
        List<GeoPoint> poly;
        Paint paint;
        public PolyItem(GeoPoint point, String label, String sublabel, List<GeoPoint> poly, Paint paint){
            super(point, label, sublabel);
            this.poly=poly;
            this.paint=paint;
        }
    }

    private ArrayList<PolyItem> overlayitems = new ArrayList<PolyItem>();

    public PolygonOverlay(){
    }

    public void addItem(PolyItem item){
        overlayitems.add(item);
    }

    public void clear(){
        overlayitems.clear();
    }
}


