package info.guardianproject.keanuapp.nearby;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pro.dbro.airshare.app.AirShareService;
import pro.dbro.airshare.session.Peer;
import pro.dbro.airshare.transport.Transport;

/**
 * Encapsulates all AirShare handling in a singleton.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class AirShareManager implements ServiceConnection, AirShareService.Callback {

    public interface Listener {
        void messageReceived(Peer sender, Payload payload);
    }

    private static AirShareManager instance;

    /**
     * Singleton getter.
     *
     * @param context A context. Only a reference to the application context will be kept.
     * @param userAlias Should be the Matrix ID of the main (first active?) account.
     * @param serviceName A shared identifier for the AirShare service. Common across platforms. (e.g. "Keanu")
     * @return The singleton instance of the AirShareManager.
     */
    public static AirShareManager getInstance(@NonNull Context context, String userAlias, String serviceName) {
        if (instance == null) {
            instance = new AirShareManager(context, userAlias, serviceName);
        }

        return instance;
    }

    /**
     * Stops the AirShareService and removes all references.
     */
    public static void destroyInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    private Context mContext;
    private final HashSet<Listener> mListeners = new HashSet<>();
    private final String mUserAlias;
    private final String mServiceName;

    private AirShareService.ServiceBinder mServiceBinder;

    private boolean mShouldListen = false;

    private final Set<Peer> mPeers = new HashSet<>();

    private boolean mWorking = false;
    private final List<AirShareMessage> mMessages = new ArrayList<>();
    private final Gson mGson = new Gson();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
                    && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON)
            {
                if (mShouldListen) startListening();
            }
        }
    };

    private AirShareManager(@NonNull Context context, String userAlias, String serviceName) {
        mContext = context.getApplicationContext();
        mUserAlias = userAlias;
        mServiceName = serviceName;

        context.registerReceiver(mBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        bindService();
    }

    /**
     * Adds a received message listener.
     *
     * @param listener Will be called when a message is received by another peer.
     * @return this for convenience.
     */
    public AirShareManager addListener(Listener listener) {
        mListeners.add(listener);

        return this;
    }

    /**
     * Removes a received message listener.
     *
     * @param listener The listener to remove.
     * @return this for convenience.
     */
    public AirShareManager removeListener(Listener listener) {
        mListeners.remove(listener);

        return this;
    }

    /**
     * Starts listening for messages as soon as the AirShareService is bound.
     *
     * @return this for convenience.
     */
    public AirShareManager startListening() {
        Log.d(getClass().getSimpleName(), "#startListening");

        mShouldListen = true;

        if (mServiceBinder == null) {
            bindService();
        }
        else {
            mServiceBinder.advertiseLocalUser();
        }

        return this;
    }

    /**
     * Stops listening for and sending messages at once.
     *
     * @return this for convenience.
     */
    public AirShareManager stop() {
        Log.d(getClass().getSimpleName(), "#stop");

        if (mServiceBinder != null) mServiceBinder.stop();

        mShouldListen = false;
        mMessages.clear();

        return this;
    }

    /**
     * Sends a text message. (Debugging only.)
     *
     * @param message A message string.
     * @return this for convenience.
     */
    public AirShareManager send(String message) {
        Log.d(getClass().getSimpleName(), String.format("#send message=%s", message));

        if (mServiceBinder == null) {
            bindService();
        }
        else {
            mServiceBinder.scanForOtherUsers();
        }

        mMessages.add(new AirShareMessage(message));

        new Worker().start();

        return this;
    }

    /**
     * JSON encodes the given {@link Payload}.
     *
     * @param payload Keanu-internal structured payload.
     * @return this for convenience.
     */
    public AirShareManager send(Payload payload) {
        return send(mGson.toJson(payload));
    }

    /**
     * Sends an invite to a room.
     *
     * @param roomAlias The canonical alias of a room.
     * @return this for convenience.
     */
    public AirShareManager sendInvite(String roomAlias) {
        return send(new Payload(new Invite(roomAlias)));
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(getClass().getSimpleName(), "#onServiceConnected");

        mServiceBinder = (AirShareService.ServiceBinder) service;

        mServiceBinder.registerLocalUserWithService(mUserAlias, mServiceName);

        mServiceBinder.setCallback(this);

        if (mShouldListen) mServiceBinder.advertiseLocalUser();

        if (!mMessages.isEmpty()) {
            mServiceBinder.scanForOtherUsers();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mServiceBinder = null;
    }

    @Override
    public void onDataReceived(@NonNull AirShareService.ServiceBinder binder, @Nullable byte[] data, @NonNull Peer sender, @Nullable Exception exception) {
        String message = new String(data, Charset.defaultCharset());

        Log.d(getClass().getSimpleName(), String.format("#onDataReceived message=%s", message));

        Payload payload = mGson.fromJson(message, Payload.class);

        for (Listener listener: mListeners) {
            listener.messageReceived(sender, payload);
        }
    }

    @Override
    public void onDataSent(@NonNull AirShareService.ServiceBinder binder, @Nullable byte[] data, @NonNull Peer recipient, @Nullable Exception exception) {
        // Ignored. There's nothing we do. Further sending just times out.
    }

    @Override
    public void onPeerStatusUpdated(@NonNull AirShareService.ServiceBinder binder, @NonNull Peer peer, @NonNull Transport.ConnectionStatus newStatus, boolean peerIsHost) {
        switch (newStatus) {
            case CONNECTED:
                mPeers.add(peer);
                break;

            case DISCONNECTED:
                mPeers.remove(peer);
                break;
        }
    }

    @Override
    public void onPeerTransportUpdated(@NonNull AirShareService.ServiceBinder binder, @NonNull Peer peer, int newTransportCode, @Nullable Exception exception) {
        // Ignored. AirShare-iOS hasn't WiFi transport implemented and AirShare-Android's also looks more than experimental.
    }

    private void bindService() {
        if (mServiceBinder == null) {
            Log.d(getClass().getSimpleName(), "bind AirShareService");

            Intent intent = new Intent(mContext, AirShareService.class);
            mContext.startService(intent);
            mContext.bindService(intent, this, 0);
        }
    }

    private void close() {
        stop();

        if (mContext != null) {
            mContext.unregisterReceiver(mBroadcastReceiver);

            mContext.unbindService(this);
            mContext.stopService(new Intent(mContext, AirShareService.class));

            mContext = null;
        }

        mListeners.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        close();

        super.finalize();
    }

    @SuppressWarnings("unused")
    static class AirShareMessage {

        byte[] message;
        long timeout;
        List<Peer> receivers = new ArrayList<>();

        AirShareMessage(@NonNull String message) {
            this(message.getBytes());
        }

        AirShareMessage(byte[] data) {
            message = data;
            timeout = Calendar.getInstance().getTimeInMillis() + 60000;
        }
    }

    class Worker extends Thread {

        @Override
        public void run() {
            Log.d(getClass().getSimpleName(), String.format("mWorking=%b", mWorking));

            if (mWorking) return;

            mWorking = true;

            long now = Calendar.getInstance().getTimeInMillis();

            for (int i = Math.max(0, mMessages.size() - 1); i > -1; i--) {
                if (mMessages.get(i).timeout < now) {
                    mMessages.remove(i);
                }
            }

            if (mMessages.isEmpty()) {
                Log.d(getClass().getSimpleName(), "Worker stopped. No more messages.");

                if (mServiceBinder != null) {
                    mServiceBinder.stop();
                    if (mShouldListen) mServiceBinder.advertiseLocalUser();
                }

                mWorking = false;

                return;
            }

            for (Peer peer: mPeers) {
                Log.d(getClass().getSimpleName(), String.format("peer=%s", peer.getAlias()));
                for (AirShareMessage message: mMessages) {
                    if (!message.receivers.contains(peer) && mServiceBinder != null) {
                        Log.d(getClass().getSimpleName(), String.format("sending message=%s to peer=%s",
                                new String(message.message, Charset.defaultCharset()), peer.getAlias()));

                        mServiceBinder.send(message.message, peer);

                        message.receivers.add(peer);
                    }
                }
            }

            mWorking = false;

            try {
                Log.d(getClass().getSimpleName(), "sleep");
                sleep(5000);
                Log.d(getClass().getSimpleName(), "start next run");

                new Worker().start();
            } catch (InterruptedException e) {
                // Ignore, we're done here.
            }
        }
    }
}
