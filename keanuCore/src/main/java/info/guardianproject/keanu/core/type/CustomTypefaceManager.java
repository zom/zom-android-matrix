package info.guardianproject.keanu.core.type;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CustomTypefaceManager {

	private static Typeface mTypeface = null;

	public static Typeface getCurrentTypeface (Context context)
	{
		return mTypeface;
	}

	public static boolean hasCustomTypeface ()
	{
		return mTypeface != null;
	}

	public static void loadFromAssets (Context context)
	{

        if (mTypeface == null) {
            String fontName = "Lato-Medium.ttf";

            try {
                mTypeface = Typeface.createFromAsset(context.getAssets(), fontName);
            } catch (Exception e) {
                Log.e("CustomTypeface", "can't find assets", e);
            }
        }
	}

	public static void setTypeface (Typeface typeface)
	{
		mTypeface = typeface;
	}

	public static void setTypefaceFromAsset (Context context, String path)
	{
		mTypeface = Typeface.createFromAsset(context.getAssets(), path);

	}

	public static void setTypefaceFromFile (Context context, String path)
	{
		File fileFont = new File(path);

		if (fileFont.exists())
			mTypeface = Typeface.createFromFile(fileFont);
	}

	public static boolean precomposeRequired ()
	{
		return (android.os.Build.VERSION.SDK_INT < 17);
	}
	
	public static String handlePrecompose (String text)
	{
		if (precomposeRequired ())
			return TibConvert.convertUnicodeToPrecomposedTibetan(text);
		else
			return text;
	}
}
