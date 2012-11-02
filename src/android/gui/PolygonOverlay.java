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

    static public class PolyItem {
        List<GeoPoint> poly;
        Paint paint;
        public PolyItem(List<GeoPoint> poly, Paint paint){
            this.poly=poly;
            this.paint=paint;
        }
    }

    private ArrayList<PolyItem> polyitems = new ArrayList<PolyItem>();

    public PolygonOverlay(){ }

    public void addItem(PolyItem item){
        polyitems.add(item);
    }

    public void draw(Canvas canvas, MapView mapview, boolean shadow){
        super.draw(canvas, mapview, shadow);
        Projection projection = mapview.getProjection();
        Point p1 = new Point();
        Point p2 = new Point();
        for(PolyItem poly: polyitems){
            Path path = new Path();
            GeoPoint st=null, px=null;
            for(GeoPoint py: poly.poly){
                if(st==null){ st=py; px=py; continue; }
                projection.toPixels(px, p1);
                projection.toPixels(py, p2);
                path.moveTo(p1.x, p1.y);
                path.lineTo(p2.x, p2.y);
                px=py;
            }
            projection.toPixels(px, p1);
            projection.toPixels(st, p2);
            path.moveTo(p1.x, p1.y);
            path.lineTo(p2.x, p2.y);
            canvas.drawPath(path, poly.paint);
        }
    }
}


