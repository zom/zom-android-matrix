package info.guardianproject.keanuapp.ui.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import info.guardianproject.keanuapp.R;


/**
 * Created by N-Pex on 2018-11-07.
 */
public class PopupDialog {
    public static Dialog showPopupFromAnchor(final View anchor, int idLayout, boolean cancelable) {
        try {
            if (anchor == null || idLayout == 0) {
                return null;
            }

            final Context context = anchor.getContext();

            View rootView = anchor.getRootView();

            Rect rectAnchor = new Rect();
            int[] location = new int[2];
            anchor.getLocationInWindow(location);
            rectAnchor.set(location[0], location[1], location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
            Rect rectRoot = new Rect();
            rootView.getLocationInWindow(location);
            rectRoot.set(location[0], location[1], location[0] + rootView.getWidth(), location[1] + rootView.getHeight());

            final Dialog dialog = new Dialog(context,
                    android.R.style.Theme_Translucent_NoTitleBar);

            View dialogView = LayoutInflater.from(context).inflate(idLayout, (ViewGroup)anchor.getRootView(), false);

            dialogView.measure(
                        View.MeasureSpec.makeMeasureSpec(rectRoot.width(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec((rectAnchor.top - rectRoot.top), View.MeasureSpec.AT_MOST));

            dialog.setTitle(null);
            dialog.setContentView(dialogView);
            dialog.setCancelable(true);

            // Setting dialogview
            Window window = dialog.getWindow();
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.x = rectAnchor.left - PopupDialog.dpToPx(20, context);
            wlp.y = rectAnchor.top - dialogView.getMeasuredHeight();
            wlp.width = dialogView.getMeasuredWidth();
            wlp.height = dialogView.getMeasuredHeight();
            wlp.gravity = Gravity.TOP | Gravity.START;
            wlp.dimAmount = 0.6f;
            wlp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(wlp);

            if (cancelable) {
                dialog.setCanceledOnTouchOutside(true);
            }

            View btnClose = dialogView.findViewById(R.id.btnClose);
            if (btnClose != null) {
                btnClose.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });
            }

            dialog.show();
            return dialog;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int dpToPx(int dp, Context ctx)
    {
        Resources r = ctx.getResources();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }
}
