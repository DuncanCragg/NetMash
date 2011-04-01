
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

public class NetMashMapOverlay extends ItemizedOverlay {

        private ArrayList<OverlayItem> overlays = new ArrayList<OverlayItem>();
        private MapActivity mapactivity;

        public NetMashMapOverlay(Drawable defaultMarker, MapActivity mapactivity){
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


