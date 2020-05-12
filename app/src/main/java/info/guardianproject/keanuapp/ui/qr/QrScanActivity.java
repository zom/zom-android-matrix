package info.guardianproject.keanuapp.ui.qr;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.zxing.Result;


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class QrScanActivity extends BaseActivity implements QrCodeDecoder.ResultCallback {

	private static String TAG = "Qrcode";

	private CameraView cameraView = null;
	private View layoutMain = null;

	private Camera camera = null;
	private boolean gotResult = false;

	private Intent dataResult = new Intent();
	ArrayList<String> resultStrings = new ArrayList<String>();

	private final static int MY_PERMISSIONS_REQUEST_CAMERA = 1;

	@Override
	protected void onCreate(Bundle state) {
        super.onCreate(state);

        setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);

        getSupportActionBar().hide();

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);

        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {

                finish();

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.

            }
        }
        else
        {
            init ();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    init();

                } else {

                    finish();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void init ()
    {

        setContentView(R.layout.awesome_activity_scan);

        layoutMain = findViewById(R.id.layout_main);

		String qrData = getIntent().getStringExtra(Intent.EXTRA_TEXT);

        LinearLayout cameraBorder = (LinearLayout)findViewById(R.id.camera_box);
		cameraView = new CameraView(this);
		cameraBorder.addView(cameraView);

		ImageView qrCodeView = (ImageView)findViewById(R.id.qr_box_image);

		new QrGenAsyncTask(this, qrCodeView, 100).executeOnExecutor(ImApp.sThreadPoolExecutor,qrData);
	}

	@Override
	protected void onResume() {
		super.onResume();
		openCamera();
	}

	@Override
	protected void onPause() {
		super.onPause();
		releaseCamera();
	}

	private synchronized void openCamera() {
		Log.d(TAG, "Opening camera");
		if (camera == null) {
			try {

				camera = Camera.open();

				cameraView.start(camera, new QrCodeDecoder(this), 0, true);
			} catch (Exception e) {
				Log.e(TAG, "Error opening camera", e);

			}
		}
	}

	private void releaseCamera() {
		Log.d(TAG, "Releasing camera");
		try {
			cameraView.stop();
			camera.release();
			camera = null;
		} catch(Exception e) {
			Log.e(TAG, "Error releasing camera", e);
			
		}
	}


	public void handleResult(final Result result) {
		runOnUiThread(new Runnable() {
			public void run() {

				gotResult = true;

				String resultString = result.getText();
                if (!resultStrings.contains(resultString)) {

                    resultStrings.add(resultString);
                    dataResult.putStringArrayListExtra("result", resultStrings);

					OnboardingManager.DecodedInviteLink diLink = null;
					try {
						diLink = OnboardingManager.decodeInviteLink(resultString);
						String message = null;

						if (diLink != null) {

							message = getString(R.string.add_contact_success,diLink.username);
						}

						if (message != null)
						{
							Snackbar.make(layoutMain, message, Snackbar.LENGTH_LONG).show();
						}

						setResult(RESULT_OK, dataResult);
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}


                }

			}
		});
	}
}