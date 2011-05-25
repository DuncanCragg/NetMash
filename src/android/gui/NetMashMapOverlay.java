
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

public class NetMashMapOverlay extends ItemizedOverlay implements DialogInterface.OnClickListener {

        private String jumpUID;

        static public class Item extends OverlayItem{
            String jumpUID;
            public Item(GeoPoint point, String label, String sublabel, String jumpUID){
                super(point, label, sublabel);
                this.jumpUID=jumpUID;
            }
        }

        private ArrayList<Item> overlayitems = new ArrayList<Item>();
        private NetMash netmash;

        public NetMashMapOverlay(Drawable defaultMarker, NetMash netmash){
            super(boundCenterBottom(defaultMarker));
            populate();
            this.netmash = netmash;
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
        protected boolean onTap(int index) {
            Item item = overlayitems.get(index);
            jumpUID=item.jumpUID;
            AlertDialog.Builder dialog = new AlertDialog.Builder(netmash);
            dialog.setTitle(item.getTitle());
            dialog.setIcon(netmash.getResources().getDrawable(R.drawable.mappinlogo));
            String[] choices = new String[3];
            choices[0]=item.getSnippet().replace("\n",", ");
            choices[1]="See this Object";
            choices[2]="Back to Map";
            dialog.setItems(choices, this);
            dialog.show();
            return true;
        }

        @Override
        public void onClick(DialogInterface dialog, int choice){
            if(choice==1) netmash.jumpToUID(jumpUID);
        }

        @Override
        protected Item createItem(int i){ return overlayitems.get(i); }

        @Override
        public int size(){ return overlayitems.size(); }
}


