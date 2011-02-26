
package appsnet.gui;

import java.io.*;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;

import jungle.platform.Kernel;
import jungle.lib.*;
import jungle.forest.FunctionalObserver;

/**  AppsNet main.
  */
public class AppsNet extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        InputStream configis = getResources().openRawResource(R.raw.jungleconfig);
        InputStream topdbis  = getResources().openRawResource(R.raw.topdb);

        JSON config=null;
        try{ config = new JSON(configis); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        String db = config.stringPathN("persist:db");
        FileOutputStream topdbos=null;
        try{ topdbos = openFileOutput(db, Context.MODE_APPEND); }catch(Exception e){ throw new RuntimeException("Local DB: "+e); }

/* http://code.google.com/p/android/issues/detail?id=9431 */
java.lang.System.setProperty("java.net.preferIPv4Stack",     "true");
java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }
}

//---------------------------------------------------------

