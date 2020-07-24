/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.keanu.core.util;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Text;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.keanu.core.plugin.ImConfigNames;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.ui.RoundedAvatarDrawable;

import static info.guardianproject.keanu.core.KeanuConstants.IMPS_CATEGORY;

public class DatabaseUtils {

    private static final String TAG = "DBUtils";

    private DatabaseUtils() {
    }

    public static Cursor queryAccountsForProvider(ContentResolver cr, String[] projection,
            long providerId) {
        StringBuilder where = new StringBuilder(Imps.Account.ACTIVE);
        where.append("=1 AND ").append(Imps.Account.PROVIDER).append('=').append(providerId);
        Cursor c = cr.query(Imps.Account.CONTENT_URI, projection, where.toString(), null, null);
        if (c != null && !c.moveToFirst()) {
            c.close();
            return null;
        }
        return c;
    }

    /**
    public static RoundedAvatarDrawable getAvatarFromCursor(Cursor cursor, int dataColumn, int width, int height) throws DecoderException {
        String hexData = cursor.getString(dataColumn);
        if (hexData.equals("NULL")) {
            return null;
        }

        byte[] data = Hex.decodeHex(hexData.substring(2, hexData.length() - 1).toCharArray());
        return decodeRoundAvatar(data, width, height);
    }

    public static BitmapDrawable getHeaderImageFromCursor(Cursor cursor, int dataColumn, int width, int height) throws DecoderException {
        String hexData = cursor.getString(dataColumn);
        if (hexData.equals("NULL")) {
            return null;
        }

        byte[] data = Hex.decodeHex(hexData.substring(2, hexData.length() - 1).toCharArray());
        return decodeSquareAvatar(data, width, height);
    }**/

    public static Drawable getAvatarFromAddress(String address, int width, int height) throws DecoderException {
        return getAvatarFromAddress(address,width,height,true);
    }

    public static Drawable getAvatarFromAddress(String address, int width, int height, boolean getRound) throws DecoderException {

        byte[] data = getAvatarBytesFromAddress(address);

        if (data != null)
            if (getRound)
              return decodeRoundAvatar(data, width, height);
            else
                return decodeSquareAvatar(data, width, height);
        else
            return null;

    }

    public final static String ROOM_AVATAR_ACCESS = "avatarcache";
    public static byte[] getAvatarBytesFromAddress(String address) throws DecoderException {

        byte[] data = null;

        if (!TextUtils.isEmpty(address)) {

            info.guardianproject.iocipher.File fileAvatar = null;
            try {
                fileAvatar = openSecureStorageFile(ROOM_AVATAR_ACCESS, address);

                if (fileAvatar.exists()) {
                    try {
                        data = new byte[(int) fileAvatar.length()];
                        BufferedInputStream buf = new BufferedInputStream(new info.guardianproject.iocipher.FileInputStream(fileAvatar));
                        buf.read(data, 0, data.length);
                        buf.close();
                        return data;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            /**
            String[] projection = {Imps.Avatars.DATA};
            String[] args = {address};
            String query = Imps.Avatars.CONTACT + " LIKE ?";
            Cursor cursor = cr.query(Imps.Avatars.CONTENT_URI, projection,
                    query, args, Imps.Avatars.DEFAULT_SORT_ORDER);


            if (cursor != null) {
                if (cursor.moveToFirst())
                    data = cursor.getBlob(0);

                cursor.close();
            }**/
        }

        return data;

    }

    /**
    public static Uri getAvatarUri(Uri baseUri, long providerId, long accountId) {
        Uri.Builder builder = baseUri.buildUpon();
        ContentUris.appendId(builder, providerId);
        ContentUris.appendId(builder, accountId);
        return builder.build();
    }**/

    public static int updateAvatarBlob(ContentResolver resolver, InputStream is,
                                       String username) {
        /**
         ContentValues values = new ContentValues(1);
         values.put(Imps.Avatars.DATA, data);

         StringBuilder buf = new StringBuilder(Imps.Avatars.CONTACT);
         buf.append(" LIKE ?");

         String[] selectionArgs = new String[] { username };

         return resolver.update(updateUri, values, buf.toString(), selectionArgs);
         **/
        if (!TextUtils.isEmpty(username)) {
            try {
                info.guardianproject.iocipher.File fileAvatar = openSecureStorageFile(ROOM_AVATAR_ACCESS, username);
                OutputStream os = new info.guardianproject.iocipher.FileOutputStream(fileAvatar);
                IOUtils.copy(is,os);
                os.close();
                return 1;
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return 0;
    }

    public static int updateAvatarBlob(ContentResolver resolver, Uri updateUri, byte[] data,
            String username) {
        /**
         ContentValues values = new ContentValues(1);
         values.put(Imps.Avatars.DATA, data);

         StringBuilder buf = new StringBuilder(Imps.Avatars.CONTACT);
         buf.append(" LIKE ?");

         String[] selectionArgs = new String[] { username };

         return resolver.update(updateUri, values, buf.toString(), selectionArgs);
         **/
        if (!TextUtils.isEmpty(username)) {
            try {
                info.guardianproject.iocipher.File fileAvatar = openSecureStorageFile(ROOM_AVATAR_ACCESS, username);
                OutputStream os = new info.guardianproject.iocipher.FileOutputStream(fileAvatar);
                IOUtils.write(data,os);
                os.close();
                return 1;
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return 0;
    }

    private static final String ReservedChars = "[|\\?*<\":>+/!']";

    public static File openSecureStorageFile(String sessionId, String filename) throws FileNotFoundException {
//        debug( "openFile: url " + url) ;
        String localFilename = "/" + sessionId + "/download/" + filename.replaceAll(ReservedChars, "_");

        //  debug( "openFile: localFilename " + localFilename) ;
        info.guardianproject.iocipher.File fileNew = new info.guardianproject.iocipher.File(localFilename);
        fileNew.getParentFile().mkdirs();

        return fileNew;
    }


    public static boolean hasAvatarContact(ContentResolver resolver, Uri updateUri,
            String username) {
        /**
        ContentValues values = new ContentValues(3);
        values.put(Imps.Avatars.CONTACT, username);

        StringBuilder buf = new StringBuilder(Imps.Avatars.CONTACT);
        buf.append("=?");

        String[] selectionArgs = new String[] { username };

        return resolver.update(updateUri, values, buf.toString(), selectionArgs) > 0;
         **/
        File fileAvatar = null;
        try {
            fileAvatar = openSecureStorageFile(ROOM_AVATAR_ACCESS,username);
            return fileAvatar.exists();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }


    }

    public static boolean doesAvatarHashExist(ContentResolver resolver, Uri queryUri,
            String jid, String hash) {

        /**
        StringBuilder buf = new StringBuilder(Imps.Avatars.CONTACT);
        buf.append("=?");
        buf.append(" AND ");
        buf.append(Imps.Avatars.HASH);
        buf.append("=?");

        String[] selectionArgs = new String[] { jid, hash };

        Cursor cursor = resolver.query(queryUri, null, buf.toString(), selectionArgs, null);
        if (cursor == null)
            return false;
        try {
            return cursor.getCount() > 0;
        } finally {
            cursor.close();
        }**/
        return false;
    }

    public static void insertAvatarBlob(ContentResolver resolver, Uri updateUri, long providerId, long accountId, byte[] data, String hash,
            String contact) {

        /**
        ContentValues values = new ContentValues(5);
        values.put(Imps.Avatars.CONTACT, contact);
        values.put(Imps.Avatars.DATA, data);
        values.put(Imps.Avatars.PROVIDER, providerId);
        values.put(Imps.Avatars.ACCOUNT, accountId);
        values.put(Imps.Avatars.HASH, hash);

        Uri resultUri = resolver.insert(updateUri, values);
         **/
        updateAvatarBlob(resolver,updateUri,data,contact);

    }

    private static BitmapDrawable decodeSquareAvatar(byte[] data, int width, int height) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length,options);
        if (b != null)
        {
            return new BitmapDrawable(b);
        }
        else
            return null;
    }

    private static RoundedAvatarDrawable decodeRoundAvatar(byte[] data, int width, int height) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length,options);
        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length,options);
        if (b != null)
        {
            RoundedAvatarDrawable avatar = new RoundedAvatarDrawable(b);
            return avatar;
        }
        else
            return null;
    }

    private static RoundedAvatarDrawable decodeRoundAvatar(InputStream data, int width, int height) {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(data, null, options);
        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;
        Bitmap b = BitmapFactory.decodeStream(data);
        if (b != null)
        {
            RoundedAvatarDrawable avatar = new RoundedAvatarDrawable(b);
            return avatar;
        }
        else
            return null;
    }


    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
    // Raw height and width of image
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {

        // Calculate ratios of height and width to requested height and width
        final int heightRatio = Math.round((float) height / (float) reqHeight);
        final int widthRatio = Math.round((float) width / (float) reqWidth);

        // Choose the smallest ratio as inSampleSize value, this will guarantee
        // a final image with both dimensions larger than or equal to the
        // requested height and width.
        inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
    }

    return inSampleSize;
}

    /**
     * Update IM provider database for a plugin using newly loaded information.
     *
     * @param cr the resolver
     * @param providerName the plugin provider name
     * @param providerFullName the full name
     * @param signUpUrl the plugin's service signup URL
     * @param config the plugin's settings
     * @return the provider ID of the plugin
     */
    public static long updateProviderDb(ContentResolver cr, String providerName,
            String providerFullName, String signUpUrl, Map<String, String> config) {
        boolean versionChanged;

        // query provider data
        long providerId = Imps.Provider.getProviderIdForName(cr, providerName);
        if (providerId > 0) {
            // already loaded, check if version changed
            String pluginVersion = config.get(ImConfigNames.PLUGIN_VERSION);
            if (!isPluginVersionChanged(cr, providerId, pluginVersion)) {
                // no change, just return
                return providerId;
            }
            // changed, update provider meta data
            updateProviderRow(cr, providerId, providerFullName, signUpUrl);
            // clear branding resource map cache
            clearBrandingResourceMapCache(cr, providerId);

            Log.d(TAG, "Plugin " + providerName + "(" + providerId
                       + ") has a version change. Database updated.");
        } else {
            // new plugin, not loaded before, insert the provider data
            providerId = insertProviderRow(cr, providerName, providerFullName, signUpUrl);

            Log.d(TAG, "Plugin " + providerName + "(" + providerId
                       + ") is new. Provider added to IM db.");
        }

        // plugin provider has been inserted/updated, we need to update settings
        saveProviderSettings(cr, providerId, config);

        return providerId;
    }

    /** Clear the branding resource map cache. */
    private static int clearBrandingResourceMapCache(ContentResolver cr, long providerId) {
        StringBuilder where = new StringBuilder();
        where.append(Imps.BrandingResourceMapCache.PROVIDER_ID);
        where.append('=');
        where.append(providerId);
        return cr.delete(Imps.BrandingResourceMapCache.CONTENT_URI, where.toString(), null);
    }

    /** Insert the plugin settings into the database. */
    private static int saveProviderSettings(ContentResolver cr, long providerId,
            Map<String, String> config) {
        ContentValues[] settingValues = new ContentValues[config.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : config.entrySet()) {
            ContentValues settingValue = new ContentValues();
            settingValue.put(Imps.ProviderSettings.PROVIDER, providerId);
            settingValue.put(Imps.ProviderSettings.NAME, entry.getKey());
            settingValue.put(Imps.ProviderSettings.VALUE, entry.getValue());
            settingValues[index++] = settingValue;
        }
        return cr.bulkInsert(Imps.ProviderSettings.CONTENT_URI, settingValues);
    }

    /** Insert a new plugin provider to the provider table. */
    private static long insertProviderRow(ContentResolver cr, String providerName,
            String providerFullName, String signUpUrl) {
        ContentValues values = new ContentValues(3);
        values.put(Imps.Provider.NAME, providerName);
        values.put(Imps.Provider.FULLNAME, providerFullName);
        values.put(Imps.Provider.CATEGORY, IMPS_CATEGORY);
        values.put(Imps.Provider.SIGNUP_URL, signUpUrl);
        Uri result = cr.insert(Imps.Provider.CONTENT_URI, values);
        return ContentUris.parseId(result);
    }

    /** Update the data of a plugin provider. */
    private static int updateProviderRow(ContentResolver cr, long providerId,
            String providerFullName, String signUpUrl) {
        // Update the full name, signup url and category each time when the plugin change
        // instead of specific version change because this is called only once.
        // It's ok to update them even the values are not changed.
        // Note that we don't update the provider name because it's used as
        // identifier at some place and the plugin should never change it.
        ContentValues values = new ContentValues(3);
        values.put(Imps.Provider.FULLNAME, providerFullName);
        values.put(Imps.Provider.SIGNUP_URL, signUpUrl);
        values.put(Imps.Provider.CATEGORY, IMPS_CATEGORY);
        Uri uri = ContentUris.withAppendedId(Imps.Provider.CONTENT_URI, providerId);
        return cr.update(uri, values, null, null);
    }

    /**
     * Compare the saved version of a plugin provider with the newly loaded
     * version.
     */
    private static boolean isPluginVersionChanged(ContentResolver cr, long providerId,
            String newVersion) {
        String oldVersion = Imps.ProviderSettings.getStringValue(cr, providerId,
                ImConfigNames.PLUGIN_VERSION);
        if (oldVersion == null) {
            return true;
        }
        return !oldVersion.equals(newVersion);
    }
}
