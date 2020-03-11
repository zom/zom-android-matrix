package info.guardianproject.keanuapp.ui.qr;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.Display;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import java.util.ArrayList;

import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.ui.BaseActivity;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.graphics.Color.WHITE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.ImageView.ScaleType.FIT_CENTER;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

@SuppressWarnings("deprecation")
public class QrDisplayActivity extends BaseActivity {

	private static String TAG = QrDisplayActivity.class.getPackage().getName();


	private LinearLayout layoutMain = null;

	private boolean gotResult = false;

	private Intent dataResult = new Intent();
	ArrayList<String> resultStrings = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);

		getSupportActionBar().hide();

		String qrData = getIntent().getStringExtra(Intent.EXTRA_TEXT);

		ImageView qrCodeView = new ImageView(this);

		qrCodeView.setScaleType(FIT_CENTER);
		qrCodeView.setBackgroundColor(WHITE);
		qrCodeView.setLayoutParams(new LayoutParams(MATCH_PARENT,
				MATCH_PARENT, 1f));

		Display display = getWindowManager().getDefaultDisplay();
		boolean portrait = display.getWidth() < display.getHeight();
		layoutMain = new LinearLayout(this);
		if(portrait) layoutMain.setOrientation(VERTICAL);
		else layoutMain.setOrientation(HORIZONTAL);
		layoutMain.setWeightSum(1);
		layoutMain.addView(qrCodeView);
		setContentView(layoutMain);

		new QrGenAsyncTask(this, qrCodeView, 240).executeOnExecutor(ImApp.sThreadPoolExecutor,qrData);
	}





}