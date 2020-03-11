package info.guardianproject.keanu.matrix.plugin;

import android.util.Log;

import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;

public class BasicApiCallback implements ApiCallback {

    private String methodId;

    private String TAG = "ApiCallback:" + methodId;

    public BasicApiCallback (String methodId)
    {
        this.methodId = methodId;
    }

    @Override
    public void onNetworkError(Exception e) {
        debug (methodId + ":onNetworkError",e);

    }

    @Override
    public void onMatrixError(MatrixError e) {
        debug (methodId + ":onMatrixError",e);

    }

    @Override
    public void onUnexpectedError(Exception e) {
        debug (methodId + ":onUnexpectedError",e);

    }

    @Override
    public void onSuccess(Object o) {
        debug (methodId + ":onSuccess: " + o);

    }

    protected void debug (String msg, MatrixError error)
    {
        Log.w(TAG, msg + ": " + error.errcode +  "=" + error.getMessage());

    }

    protected void debug (String msg)
    {
        Log.d(TAG, msg);
    }

    protected void debug (String msg, Exception e)
    {
        Log.e(TAG, msg, e);
    }

}
