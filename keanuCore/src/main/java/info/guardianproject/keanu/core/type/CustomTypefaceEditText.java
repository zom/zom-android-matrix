package info.guardianproject.keanu.core.type;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.core.os.BuildCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

public class CustomTypefaceEditText extends androidx.appcompat.widget.AppCompatEditText {

    boolean mInit = false;
    int themeColorText = -1;
    private OnReachContentSelect onReachContentSelect;
    public interface OnReachContentSelect{
        void onReachContentClick(InputContentInfoCompat inputContentInfoCompat);
    }
    public CustomTypefaceEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
      

       init();
    }

    public CustomTypefaceEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		

	       init();
	}



	public CustomTypefaceEditText(Context context) {
		super(context);
		

	       init();
	}
	
	


	private void init() {

        if (!mInit) {
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
            themeColorText = settings.getInt("themeColorText",-1);

            Typeface t = CustomTypefaceManager.getCurrentTypeface(getContext());

            if (t != null)
                setTypeface(t);

            mInit = true;
        }


		if (themeColorText > 0 || themeColorText < -1)
			setTextColor(themeColorText);
    	

    }

    public void setReachContentClickListner(OnReachContentSelect onReachContentSelect){
        this.onReachContentSelect = onReachContentSelect;
    }
	@Override
	public void setText(CharSequence text, BufferType type) {

		super.setText(text, type);

		if (themeColorText > 0 || themeColorText < -1)
			setTextColor(themeColorText);

	}

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        final InputConnection ic = super.onCreateInputConnection(editorInfo);
        EditorInfoCompat.setContentMimeTypes(editorInfo,
                new String [] { "image/gif"});

        final InputConnectionCompat.OnCommitContentListener callback =
                new InputConnectionCompat.OnCommitContentListener() {
                    @Override
                    public boolean onCommitContent(InputContentInfoCompat inputContentInfo,
                                                   int flags, Bundle opts) {
                        // read and display inputContentInfo asynchronously
                        if (BuildCompat.isAtLeastNMR1() && (flags &
                                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                            try {
                                inputContentInfo.requestPermission();
                            }
                            catch (Exception e) {
                                return false; // return false if failed
                            }
                        }
                       /* Log.v("InputConnection","InputConnection=="+inputContentInfo.getContentUri());
                        Log.v("InputConnection","LinkUri=="+inputContentInfo.getLinkUri());
                        Log.v("InputConnection","Description=="+inputContentInfo.getDescription());*/
                        if(onReachContentSelect!=null){
                            onReachContentSelect.onReachContentClick(inputContentInfo);
                        }

                        // read and display inputContentInfo asynchronously.
                        // call inputContentInfo.releasePermission() as needed.

                        return true;  // return true if succeeded
                    }
                };
        return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
    }

}