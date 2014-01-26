package cyrus;

import java.util.*;

import android.hardware.*;
import android.content.*;
import static android.content.Context.*;

import cyrus.gui.Cyrus;
import static cyrus.lib.Utils.*;

public class Sensors implements SensorEventListener {

    User user;
    SensorManager sensorManager=null;

    public Sensors(User user){
        this.user=user;
        sensorManager=(SensorManager)Cyrus.top.getSystemService(Context.SENSOR_SERVICE);
    }

    public void startWatchingSensors(){
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),  SensorManager.SENSOR_DELAY_UI);
    }

    public void stopWatchingSensors(){
        sensorManager.unregisterListener(this);
    }

    float[] acceldata;
    float[] magnedata;

    float R1[]          = new float[9];
    float R2[]          = new float[9];
    float orientation[] = new float[3];

    float azim;
    float pitc;
    float roll;

    public void onSensorChanged(SensorEvent evt) {
        if(evt.sensor.getType()==Sensor.TYPE_ACCELEROMETER ) acceldata = evt.values.clone();
        if(evt.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD) magnedata = evt.values.clone();
        if(acceldata!=null && magnedata!=null){
            if(SensorManager.getRotationMatrix(R1, null, acceldata, magnedata)){
                SensorManager.remapCoordinateSystem(R1, SensorManager.AXIS_X, SensorManager.AXIS_Z, R2);
                SensorManager.getOrientation(R2, orientation);
                azim=smooth(azim,orientation[0]);
                pitc=smooth(pitc,orientation[1]);
                roll=smooth(roll,orientation[2]);
                user.onNewOrientation(azim, pitc, roll);
            }
            acceldata=null; magnedata=null;
        }
    }

    float smooth(float o, float n){
        if(o < -1.5f && n >  1.5f) o+=2*Math.PI;
        if(o >  1.5f && n < -1.5f) o-=2*Math.PI;
        return 0.7f*o+0.3f*n;
    }

    public void onAccuracyChanged(Sensor s, int x) {}
}


