package info.guardianproject.keanuapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.gson.Gson;

import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.nearby.AirShareManager;
import info.guardianproject.keanuapp.nearby.Payload;
import info.guardianproject.keanuapp.ui.gallery.GalleryActivity;
import pro.dbro.airshare.session.Peer;
import pro.dbro.airshare.transport.ble.BleUtil;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;

public class MoreFragment extends Fragment implements AirShareManager.Listener {

    private static final boolean AIR_SHARE_TEST = false;

    private static final int PERMISSION_REQUEST_CODE = 666;

    private AirShareManager mAirShareManager;

    public MoreFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_more, container, false);


        view.findViewById(R.id.btnOpenGallery)
                .setOnClickListener(btnOpenGallery -> {
                    Context context = getContext();
                    if (context == null) return;

                    context.startActivity(new Intent(context, GalleryActivity.class));
                });

        view.findViewById(R.id.btnOpenServices)
                .setOnClickListener(btnOpenServices -> openServices());

        view.findViewById(R.id.btnOpenGroups)
                .setOnClickListener(btnOpenGroups -> {
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity == null) return;

                    activity.startGroupChat();
                });

//        view.findViewById(R.id.btnCreateSession)
//                .setOnClickListener(btnCreateSession -> {
//                    MainActivity activity = (MainActivity) getActivity();
//                    if (activity == null) return;
//
//                    activity.startSession();
//                });

        view.findViewById(R.id.btnOpenStickers)
                .setOnClickListener(btnOpenStickers -> {
                    Context context = getContext();
                    if (context == null) return;

                    context.startActivity(new Intent(context, StickerActivity.class));
                });

        view.findViewById(R.id.btnOpenThemes)
                .setOnClickListener(btnOpenThemes -> showColors());

        btn = view.findViewById(R.id.btnOpenStickers);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
        if (AIR_SHARE_TEST) {
            view.findViewById(R.id.btnAirShareTest)
                    .setOnClickListener(btnAirShareTest -> airShareTest());

            Activity activity = getActivity();
            if (activity != null) {
                String alias = ((ImApp) activity.getApplication()).getDefaultUsername();
                String serviceName = getString(R.string.app_name); // FIXME: This will cause problems when app_name gets translated.

                Log.d(getClass().getSimpleName(), String.format("Starting AirShareManager with alias=%s, serviceName=%s", alias, serviceName));

                mAirShareManager = AirShareManager.getInstance(activity, alias, serviceName)
                        .addListener(this)
                        .startListening();
            }
        }
        else {
            view.findViewById(R.id.btnAirShareTest).setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getContext();

        if (AIR_SHARE_TEST && context != null && !BleUtil.isBluetoothEnabled(context)) {
            context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    @Override
    public void onDestroy() {
        AirShareManager.destroyInstance();

        super.onDestroy();
    }

    private void showColors () {
        Context context = getContext();
        if (context == null) return;

        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

        int selColor = settings.getInt("themeColor",-1);

        ColorPickerDialogBuilder
                .with(context)
                .setTitle("Choose color")
                .initialColor(selColor)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .lightnessSliderOnly()
                .setOnColorSelectedListener(selectedColor -> {})
                .setPositiveButton(getString(R.string.ok), (dialog, selectedColor, allColors) -> {
                    settings.edit().putInt("themeColor",selectedColor).apply();

                    /*
                    int textColor = getContrastColor(selectedColor);
                    int bgColor = getContrastColor(textColor);

                    settings.edit().putInt("themeColorBg",bgColor).commit();
                    settings.edit().putInt("themeColorText",textColor).commit();
                     */

                    MainActivity activity = (MainActivity)getActivity();
                    if (activity != null)  activity.applyStyle();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .build()
                .show();
    }

    private void openServices() {
        Context context = getContext();
        if (context == null) return;

        context.startActivity(new Intent(context, ServicesActivity.class));
    }

    private void airShareTest() {
        Log.d(getClass().getSimpleName(), "#airShareTest start");

        Context context = getContext();
        if (context == null) return;

        if (ContextCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { ACCESS_COARSE_LOCATION }, PERMISSION_REQUEST_CODE);

            return;
        }

//        Payload payload = new Payload();
//        payload.testData = new HugeTestData(context);
//
//        mAirShareManager.send(payload);

        mAirShareManager.sendInvite("#example_room_alias:neo.keanu.im");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Context context = getContext();
        if (context == null) return;

        Log.d(getClass().getSimpleName(), String.format("#onRequestPermissionsResult requestCode=%d", requestCode));

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                airShareTest();
            }
            else {
                new AlertDialog.Builder(context)
                        .setTitle("AirShare")
                        .setMessage("Need coarse location permissions to send AirShare messages!")
                        .setNeutralButton(android.R.string.ok, null)
                        .create()
                        .show();
            }

            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void messageReceived(Peer sender, Payload payload) {
        Context context = getContext();
        if (context == null) return;

        new AlertDialog.Builder(context)
                .setTitle(String.format("Message from %s", sender.getAlias()))
                .setMessage(new Gson().toJson(payload))
                .setNeutralButton(android.R.string.ok, null)
                .create()
                .show();
    }
}
