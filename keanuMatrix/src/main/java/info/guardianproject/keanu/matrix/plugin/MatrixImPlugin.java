package info.guardianproject.keanu.matrix.plugin;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import info.guardianproject.keanu.core.plugin.ImConfigNames;
import info.guardianproject.keanu.core.plugin.ImPlugin;
import info.guardianproject.keanu.core.plugin.ImpsConfigNames;


/** Simple example of writing a plug-in for the IM application. */
public class MatrixImPlugin extends Service implements ImPlugin {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** The implementation of IImPlugin defined through AIDL. */
    public Map getProviderConfig() {
        HashMap<String, String> config = new HashMap<String, String>();
        // The protocol name MUST be IMPS now.
        config.put(ImConfigNames.PROTOCOL_NAME, "MATRIX");
        config.put(ImConfigNames.PLUGIN_VERSION, "0.1");
        config.put(ImpsConfigNames.HOST, "http://matrix.org");
        config.put(ImpsConfigNames.SUPPORT_USER_DEFINED_PRESENCE, "false");
     //   config.put(ImpsConfigNames.CUSTOM_PRESENCE_MAPPING,
       //         "org.awesomeapp.messenger.plugin.xmpp.XmppPresenceMapping");
        return config;
    }

    public Map getResourceMap() {
        HashMap<Integer, Integer> resMapping = new HashMap<Integer, Integer>();

        return resMapping;
    }

}