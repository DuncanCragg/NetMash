
package android.gui;

import java.util.*;

import android.os.*;
import android.net.*;
import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;

import android.view.*;
import android.view.View.*;
import android.widget.*;

import com.google.android.maps.*;

public class NetMashMap extends MapActivity {

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);
        MapView mapView = (MapView)findViewById(R.id.MapView);
        mapView.setBuiltInZoomControls(true);

        GeoPoint point = new GeoPoint(19240000,-99120000);
        OverlayItem overlayitem = new OverlayItem(point, "Hola, Mundo!", "I'm in Mexico City!");
        GeoPoint point2 = new GeoPoint(35410000, 139460000);
        OverlayItem overlayitem2 = new OverlayItem(point2, "Sekai, konichiwa!", "I'm in Japan!");

        Drawable drawable = this.getResources().getDrawable(R.drawable.icon);
        NetMashMap.Overlay itemizedoverlay = new NetMashMap.Overlay(drawable, this);
        itemizedoverlay.addItem(overlayitem);
        itemizedoverlay.addItem(overlayitem2);
        mapView.getOverlays().add(itemizedoverlay);
    }

    public class Overlay extends ItemizedOverlay {

        private ArrayList<OverlayItem> overlays = new ArrayList<OverlayItem>();
        private MapActivity mapactivity;

        public Overlay(Drawable defaultMarker, MapActivity mapactivity){
            super(boundCenter(defaultMarker));
            this.mapactivity = mapactivity;
        }

        public void addItem(OverlayItem item){
            overlays.add(item);
            populate();
        }

        @Override
        protected boolean onTap(int index) {
            OverlayItem item = overlays.get(index);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mapactivity);
            dialog.setTitle(item.getTitle());
            dialog.setMessage(item.getSnippet());
            dialog.show();
            return true;
        }

        @Override
        protected OverlayItem createItem(int i){ return overlays.get(i); }

        @Override
        public int size(){ return overlays.size(); }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
}

