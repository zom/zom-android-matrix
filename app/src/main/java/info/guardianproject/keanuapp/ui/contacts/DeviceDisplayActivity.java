package info.guardianproject.keanuapp.ui.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.commons.codec.DecoderException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatListener;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.service.adapters.ChatListenerAdapter;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanu.matrix.plugin.MatrixAddress;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;
import info.guardianproject.keanuapp.ui.qr.QrShareAsyncTask;
import info.guardianproject.keanuapp.ui.widgets.GroupAvatar;
import info.guardianproject.keanuapp.ui.widgets.LetterAvatar;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.SMALL_AVATAR_WIDTH;

public class DeviceDisplayActivity extends BaseActivity {

    private String mName = null;
    private String mAddress = null;
    private long mProviderId = -1;
    private long mAccountId = -1;
    private String mLocalAddress = null;

    private IImConnection mConn;
    private IChatSession mSession;

    private class DeviceDisplay {
        public String deviceName;
        public String deviceId;
        public String deviceFingerprint;
        public boolean isVerified = false;
    }

    private RecyclerView mRecyclerView;
    private ArrayList<DeviceDisplay> mDevices;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("");
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_devices);

        mName = getIntent().getStringExtra("nickname");
        mAddress = getIntent().getStringExtra("address");
        mProviderId = getIntent().getLongExtra("provider", -1);
        mAccountId = getIntent().getLongExtra("account", -1);

        Cursor cursor = getContentResolver().query(Imps.ProviderSettings.CONTENT_URI, new String[]{Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE}, Imps.ProviderSettings.PROVIDER + "=?", new String[]{Long.toString(mProviderId)}, null);

        if (cursor == null)
            return; //not going to work

        Imps.ProviderSettings.QueryMap providerSettings = new Imps.ProviderSettings.QueryMap(
                cursor, getContentResolver(), mProviderId, false, null);

        mDevices = new ArrayList<>();
        mConn = RemoteImService.getConnection(mProviderId, mAccountId);
        mLocalAddress = '@' + Imps.Account.getUserName(getContentResolver(), mAccountId) + ':' + providerSettings.getDomain();

        providerSettings.close();

       getSupportActionBar().setTitle(mName);

        mRecyclerView = findViewById(R.id.rvRoot);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {

            private static final int VIEW_TYPE_MEMBER = 0;
            private static final int VIEW_TYPE_HEADER = 1;
            private static final int VIEW_TYPE_FOOTER = 2;

            private int colorTextPrimary = 0xff000000;

            public RecyclerView.Adapter init() {
                TypedValue out = new TypedValue();
                getTheme().resolveAttribute(R.attr.contactTextPrimary, out, true);
                colorTextPrimary = out.data;
                return this;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                switch (viewType) {
                    case VIEW_TYPE_HEADER:
                        return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_devices_header, parent, false));
                    case VIEW_TYPE_FOOTER:
                        return new FooterViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_devices_footer, parent, false));
                }
                return new DeviceViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.device_view, parent, false));
            }

            @Override
            public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof HeaderViewHolder) {
                    final HeaderViewHolder h = (HeaderViewHolder)holder;

                    h.userAddress.setText(mAddress);


                } else if (holder instanceof FooterViewHolder) {
                    FooterViewHolder h = (FooterViewHolder)holder;


                } else if (holder instanceof DeviceViewHolder) {
                    DeviceViewHolder h = (DeviceViewHolder) holder;

                    // Reset the padding to match other views in this hierarchy
                    //
                    int padding = getResources().getDimensionPixelOffset(R.dimen.detail_view_padding);
                    h.itemView.setPadding(padding, h.itemView.getPaddingTop(), padding, h.itemView.getPaddingBottom());

                    int idxMember = position - 1;
                    final DeviceDisplay deviceDisplay = mDevices.get(idxMember);

                    if (!TextUtils.isEmpty(deviceDisplay.deviceId))
                        h.tvDeviceName.setText(deviceDisplay.deviceName + " (" + deviceDisplay.deviceId + ")");
                    else
                        h.tvDeviceName.setText(deviceDisplay.deviceId);

                    h.tvDeviceFingerprint.setText(deviceDisplay.deviceFingerprint);

                    if (deviceDisplay.isVerified) {
                        h.imgVerifiedIcon.setColorFilter(ContextCompat.getColor(DeviceDisplayActivity.this, R.color.holo_green_light), android.graphics.PorterDuff.Mode.MULTIPLY);
                        h.scVerified.setChecked(deviceDisplay.isVerified);
                    }

                    h.scVerified.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (mConn != null) {
                                try {
                                    mConn.setDeviceVerified(mAddress,deviceDisplay.deviceId,isChecked);

                                    h.imgVerifiedIcon.setColorFilter(ContextCompat.getColor(DeviceDisplayActivity.this,
                                            isChecked? R.color.holo_green_light : R.color.holo_grey_light), android.graphics.PorterDuff.Mode.MULTIPLY);


                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });


                }
            }

            @Override
            public int getItemCount() {
                return 2 + mDevices.size();
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0)
                    return VIEW_TYPE_HEADER;
                else if (position == getItemCount() - 1)
                    return VIEW_TYPE_FOOTER;
                return VIEW_TYPE_MEMBER;
            }
        }.init());

    }


    @Override
    protected void onResume() {
        super.onResume();
        updateDevices();
    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    private void updateDevices ()
    {
        if (mConn != null) {
            new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... params) {

                    try {


                        List<String> devices = mConn.getFingerprints(params[0]);
                        if (devices != null && devices.size() > 0) {
                            for (String device : devices) {
                                StringTokenizer st = new StringTokenizer(device, "|");
                                DeviceDisplay deviceDisplay = new DeviceDisplay();

                                deviceDisplay.deviceName = st.nextToken();
                                deviceDisplay.deviceId = st.nextToken();
                                deviceDisplay.deviceFingerprint = prettyPrintFingerprint(st.nextToken() + " ");
                                deviceDisplay.isVerified = Boolean.parseBoolean(st.nextToken());

                                mDevices.add(deviceDisplay);

                            }

                            return true;
                        }
                    } catch (RemoteException re) {

                        return false;
                    }

                    return false;

                }

                @Override
                protected void onPostExecute(Boolean success) {
                    super.onPostExecute(success);

                    if (success && mDevices.size() > 0)
                        mRecyclerView.getAdapter().notifyDataSetChanged();
                }
            }.execute(mAddress);
        }

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final ImageView qr;
        final TextView userAddress;

        HeaderViewHolder(View view) {
            super(view);
            avatar = (ImageView) view.findViewById(R.id.ivAvatar);
            qr = (ImageView) view.findViewById(R.id.qrcode);
            userAddress = (TextView) view.findViewById(R.id.tvUserAddress);
        }
    }

    private class FooterViewHolder extends RecyclerView.ViewHolder {


        FooterViewHolder(View view) {
            super(view);

        }
    }

    private class DeviceViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDeviceName;
        final TextView tvDeviceFingerprint;
        final ImageView imgVerifiedIcon;
        final SwitchCompat scVerified;

        DeviceViewHolder(View view) {
            super(view);
            tvDeviceName = (TextView) view.findViewById(R.id.tvDeviceName);
            tvDeviceFingerprint = (TextView) view.findViewById(R.id.tvDeviceFingerprint);
            imgVerifiedIcon = (ImageView) view.findViewById(R.id.verifiedIcon);
            scVerified = view.findViewById(R.id.switchVerified);
        }
    }


    private String prettyPrintFingerprint(String fingerprint) {
        StringBuffer spacedFingerprint = new StringBuffer();

        int groupSize = 4;

        for (int i = 0; i + groupSize <= fingerprint.length(); i += groupSize) {
            spacedFingerprint.append(fingerprint.subSequence(i, i + groupSize));
            spacedFingerprint.append(' ');
        }

        return spacedFingerprint.toString();
    }


}
