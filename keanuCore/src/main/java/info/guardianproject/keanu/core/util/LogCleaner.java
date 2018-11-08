package info.guardianproject.keanu.core.util;

import android.util.Log;

import java.net.URLEncoder;

public class LogCleaner {
    public static String clean (String msg)
    {

        if (Debug.DEBUG_ENABLED)
            return msg;
        else
            return URLEncoder.encode(msg);

    }

    public static void warn (String tag, String msg)
    {

            Log.w(tag, clean(msg));
    }

    public static void debug (String tag, String msg)
    {
        if (Debug.DEBUG_ENABLED)
            Log.d(tag, clean(msg));
    }

    public static void error (String tag, String msg, Exception e)
    {
        Log.e(tag, clean(msg),e);
    }


    public static void error (String tag, String msg, Throwable e)
    {
        Log.e(tag, clean(msg),e);
    }
}
