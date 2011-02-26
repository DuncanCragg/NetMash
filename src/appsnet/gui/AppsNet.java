
package appsnet.gui;

import java.io.*;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;

import jungle.platform.Kernel;
import jungle.lib.JSON;
import jungle.forest.FunctionalObserver;

import appsnet.User;

/**  AppsNet main.
  */
public class AppsNet extends Activity
{
    static public AppsNet top=null;

    public User user=null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        top=this;
        if(!Kernel.running) runKernel();
        else if(user!=null) user.onTopCreate();
    }

    @Override
    public void onStart()
    {
        super.onStart();
System.out.println("onStart");
    }

    @Override
    public void onRestart()
    {
        super.onRestart();
System.out.println("onRestart");
    }

    @Override
    public void onPause()
    {
        super.onPause();
System.out.println("onPause");
    }

    @Override
    public void onResume()
    {
        super.onResume();
System.out.println("onResume");
    }

    @Override
    public void onStop()
    {
        super.onStop();
System.out.println("onStop");
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
System.out.println("onDestroy");
        top=null;
        if(user!=null) user.onTopDestroy();
    }

    //---------------------------------------------------------

    private void runKernel(){

        InputStream configis = getResources().openRawResource(R.raw.jungleconfig);
        JSON config=null;
        try{ config = new JSON(configis); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        String db = config.stringPathN("persist:db");

        InputStream topdbis=null;
        try{ topdbis = openFileInput(db); }catch(Exception e){ }
        if(topdbis==null) topdbis = getResources().openRawResource(R.raw.topdb);

        FileOutputStream topdbos=null;
        try{ topdbos = openFileOutput(db, Context.MODE_APPEND); }catch(Exception e){ throw new RuntimeException("Local DB: "+e); }

        workaroundForFroyoBug();

        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }

    private void workaroundForFroyoBug(){
        /* http://code.google.com/p/android/issues/detail?id=9431 */
        java.lang.System.setProperty("java.net.preferIPv4Stack",     "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
    }
}

//---------------------------------------------------------

