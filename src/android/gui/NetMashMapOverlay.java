
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

        private ArrayList<OverlayItem> overlayitems = new ArrayList<OverlayItem>();
        private MapActivity mapactivity;

        public NetMashMapOverlay(Drawable defaultMarker, MapActivity mapactivity){
            super(boundCenterBottom(defaultMarker));
            populate();
            this.mapactivity = mapactivity;
        }

        public void addItem(OverlayItem item){
            overlayitems.add(item);
            populate();
        }

        public void clear(){
            overlayitems.clear();
            populate();
        }

        @Override
        protected boolean onTap(int index) {
            OverlayItem item = overlayitems.get(index);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mapactivity);
            dialog.setTitle(item.getTitle());
            dialog.setMessage(item.getSnippet());
            dialog.show();
            return true;
        }

        @Override
        protected OverlayItem createItem(int i){ return overlayitems.get(i); }

        @Override
        public int size(){ return overlayitems.size(); }
}


