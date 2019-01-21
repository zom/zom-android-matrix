package info.guardianproject.keanu.core.plugin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import java.util.HashMap;

import info.guardianproject.keanu.core.model.ProviderDef;
import info.guardianproject.keanu.core.provider.Imps;

import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;

public class ProviderManager {

    private static HashMap<Long, ProviderDef> mProviders;

    private static final String[] PROVIDER_PROJECTION = { Imps.Provider._ID, Imps.Provider.NAME,
            Imps.Provider.FULLNAME,
            Imps.Provider.SIGNUP_URL, };

    private static synchronized void loadImProviderSettings(Context context) {

        mProviders = new HashMap<Long, ProviderDef>();
        ContentResolver cr = context.getContentResolver();

        String selectionArgs[] = new String[1];
        selectionArgs[0] = IMPS_CATEGORY;

        Cursor c = cr.query(Imps.Provider.CONTENT_URI, PROVIDER_PROJECTION, Imps.Provider.CATEGORY
                        + "=?", selectionArgs,
                null);
        if (c == null) {
            return;
        }

        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String providerName = c.getString(1);
                String fullName = c.getString(2);
                String signUpUrl = c.getString(3);

                if (mProviders == null) // mProviders has been reset
                    break;
                mProviders.put(id, new ProviderDef(id, providerName, fullName, signUpUrl));
            }
        } finally {
            c.close();
        }
    }

    public static long getProviderId(Context context, String name) {
        loadImProviderSettings(context);
        for (ProviderDef provider : mProviders.values()) {
            if (provider.mName.equals(name)) {
                return provider.mId;
            }
        }
        return -1;
    }

    public static ProviderDef getProvider(Context context, long id) {
        loadImProviderSettings(context);
        return mProviders.get(id);
    }
}
