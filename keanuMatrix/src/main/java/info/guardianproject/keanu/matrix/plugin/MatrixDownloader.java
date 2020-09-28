package info.guardianproject.keanu.matrix.plugin;


import android.net.TrafficStats;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.URLUtil;

import org.apache.commons.io.IOUtils;
import org.matrix.androidsdk.crypto.model.crypto.EncryptedFileInfo;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import info.guardianproject.iocipher.File;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;

public class MatrixDownloader {


    private String mMimeType = null;
    private static final int DOWNLOADER_THREAD_ID = 10010;

    public MatrixDownloader()
    {}

    public boolean get (String urlString, OutputStream storageStream) throws IOException
    {
        try {


            TrafficStats.setThreadStatsTag(DOWNLOADER_THREAD_ID);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).build();

            Request request = new Request.Builder().url(urlString)

                    .build();
            Response response = client.newCall(request).execute();

            InputStream in = new BufferedInputStream(response.body().byteStream());

            mMimeType = response.body().contentType().toString();

//            long contentLength = response.body().contentLength();

            IOUtils.copy(in,storageStream);
            storageStream.flush();
            storageStream.close();
            response.body().close();

            return true;

        } catch (Exception e) {

            Log.d("Download","Error downloading media",e);
            return false;
        }
    }

    public String getMimeType ()
    {
        return mMimeType;
    }

    public File openSecureStorageFile(String sessionId, String filename) throws FileNotFoundException {
//        debug( "openFile: url " + url) ;

        File fileMedia = SecureMediaStore.checkDownloadExists(sessionId, filename);
        if (fileMedia == null || fileMedia.length() == 0)
        {
            String localFilename = SecureMediaStore.getDownloadFilename(sessionId, filename);
            //  debug( "openFile: localFilename " + localFilename) ;
            fileMedia = new info.guardianproject.iocipher.File(localFilename);
            fileMedia.getParentFile().mkdirs();
        }

        return fileMedia;
    }

    private String getFilenameFromUrl(String urlString) {
        String fileName = URLUtil.guessFileName(urlString, null, null);

        if (fileName.contains("#"))
            return fileName.split("#")[0];
        else if (fileName.contains("?"))
            return fileName.split("\\?")[0];
        else
            return fileName;

    }

    public static boolean decryptAttachment(InputStream attachmentStream, EncryptedFileInfo encryptedFileInfo, OutputStream outStream) {
        if (null != attachmentStream && null != encryptedFileInfo) {
            if (!TextUtils.isEmpty(encryptedFileInfo.iv) && null != encryptedFileInfo.key && null != encryptedFileInfo.hashes && encryptedFileInfo.hashes.containsKey("sha256")) {
                if (TextUtils.equals(encryptedFileInfo.key.alg, "A256CTR") && TextUtils.equals(encryptedFileInfo.key.kty, "oct") && !TextUtils.isEmpty(encryptedFileInfo.key.k)) {
                    try {
                        if (0 == attachmentStream.available()) {
                            return false;
                        }
                    } catch (Exception var17) {
                       Log.e(LOG_TAG, "Fail to retrieve the file size", var17);
                    }

                    long t0 = System.currentTimeMillis();

                    try {
                        byte[] key = Base64.decode(base64UrlToBase64(encryptedFileInfo.key.k), 0);
                        byte[] initVectorBytes = Base64.decode(encryptedFileInfo.iv, 0);
                        Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
                        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
                        IvParameterSpec ivParameterSpec = new IvParameterSpec(initVectorBytes);
                        decryptCipher.init(2, secretKeySpec, ivParameterSpec);
                        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                        byte[] data = new byte['耀'];

                        int read;
                        byte[] decodedBytes;
                        while(-1 != (read = attachmentStream.read(data))) {
                            messageDigest.update(data, 0, read);
                            decodedBytes = decryptCipher.update(data, 0, read);
                            outStream.write(decodedBytes);
                        }

                        decodedBytes = decryptCipher.doFinal();
                        outStream.write(decodedBytes);
                        String currentDigestValue = base64ToUnpaddedBase64(Base64.encodeToString(messageDigest.digest(), 0));
                        if (!TextUtils.equals((CharSequence)encryptedFileInfo.hashes.get("sha256"), currentDigestValue)) {
                            Log.e(LOG_TAG, "## decryptAttachment() :  Digest value mismatch");
                            outStream.close();
                            return false;
                        }

                        Log.d(LOG_TAG, "Decrypt in " + (System.currentTimeMillis() - t0) + " ms");
                        return true;
                    } catch (OutOfMemoryError var18) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  failed " + var18.getMessage(), var18);
                    } catch (Exception var19) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  failed " + var19.getMessage(), var19);
                    }

                    try {
                        outStream.close();
                    } catch (Exception var16) {
                        Log.e(LOG_TAG, "## decryptAttachment() :  fail to close the file", var16);
                    }

                    return false;
                } else {
                    Log.e(LOG_TAG, "## decryptAttachment() : invalid key fields");
                    return false;
                }
            } else {
                Log.e(LOG_TAG, "## decryptAttachment() : some fields are not defined");
                return false;
            }
        } else {
            Log.e(LOG_TAG, "## decryptAttachment() : null parameters");
            return false;
        }
    }

    private static String base64UrlToBase64(String base64Url) {
        if (null != base64Url) {
            base64Url = base64Url.replaceAll("-", "+");
            base64Url = base64Url.replaceAll("_", "/");
        }

        return base64Url;
    }

    private static String base64ToBase64Url(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("\\+", "-");
            base64 = base64.replaceAll("/", "_");
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }

    private static String base64ToUnpaddedBase64(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }

    private final static String LOG_TAG = "MXDL";

}