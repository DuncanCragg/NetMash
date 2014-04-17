
package cyrus;

import java.util.*;
import java.util.regex.*;

import android.os.*;

import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;

import static android.content.Context.*;
import static android.location.LocationManager.*;

import cyrus.gui.NetMash;

import cyrus.lib.JSON;
import cyrus.forest.*;
import cyrus.platform.Kernel;

/** Algorithm for GPS.
  */
public class CurrentLocation implements LocationListener {

    private static final int SECONDS = 1000;
    private static final int MINUTES = 60 * SECONDS;

    private User user;
    private LocationManager locationManager;
    private Location currentLocation;

    CurrentLocation(User user){
        this.user=user;
        locationManager=(LocationManager)NetMash.top.getSystemService(LOCATION_SERVICE);
    }

    public void getLocationUpdates(){
        Location netloc=locationManager.getLastKnownLocation(NETWORK_PROVIDER);
        Location gpsloc=locationManager.getLastKnownLocation(GPS_PROVIDER);
        currentLocation = isBetterLocation(netloc, gpsloc)? netloc: gpsloc;
        if(currentLocation!=null) user.onNewLocation(currentLocation);
        // http://code.google.com/p/android/issues/detail?id=19857
        try{ locationManager.requestLocationUpdates(NETWORK_PROVIDER, 15*SECONDS, 0, this); }catch(Exception e){}
        try{ locationManager.requestLocationUpdates(GPS_PROVIDER,     15*SECONDS, 0, this); }catch(Exception e){}
    }

    public void stopLocationUpdates(){
        locationManager.removeUpdates(this);
    }

    public void onLocationChanged(Location location){
        if(!isBetterLocation(location, currentLocation)) return;
        if( hasMovedLocation(location, currentLocation)) user.onNewLocation(location);
        currentLocation=location;
    }

    public void onStatusChanged(String provider, int status, Bundle extras){}
    public void onProviderEnabled(String provider){}
    public void onProviderDisabled(String provider){}

    protected boolean isBetterLocation(Location newLocation, Location prevLocation) {

        if(newLocation ==null) return false;
        if(prevLocation==null) return true;

        long timeDelta = newLocation.getTime() - prevLocation.getTime();
        boolean isSignificantlyNewer = timeDelta >  (2*MINUTES);
        boolean isSignificantlyOlder = timeDelta < -(2*MINUTES);
        boolean isNewer              = timeDelta > 0;
        if(isSignificantlyNewer) return true;
        if(isSignificantlyOlder) return false;

        int accuracyDelta=(int)(newLocation.getAccuracy() - prevLocation.getAccuracy());
        boolean isMoreAccurate              = accuracyDelta < 0;
        boolean isLessAccurate              = accuracyDelta > 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;
        boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), prevLocation.getProvider());
        if(isMoreAccurate) return true;
        if(isNewer && !isLessAccurate) return true;
        if(isNewer && !isSignificantlyLessAccurate && isFromSameProvider) return true;
        return false;
    }

    private boolean hasMovedLocation(Location newLocation, Location prevLocation){
        if(prevLocation==null) return newLocation!=null;
        if(newLocation.getLatitude() !=prevLocation.getLatitude() ) return true;
        if(newLocation.getLongitude()!=prevLocation.getLongitude()) return true;
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2){
        if(provider1==null) return provider2==null;
        return provider1.equals(provider2);
    }
}

