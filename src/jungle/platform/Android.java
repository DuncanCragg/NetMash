
package jungle.platform;

import java.io.*;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;

import jungle.platform.Kernel;
import jungle.lib.*;
import jungle.forest.FunctionalObserver;

/**  Jungle: FOREST Reference Implementation; Android main.
  */
public class Android extends Activity
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

        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }
}

//---------------------------------------------------------

