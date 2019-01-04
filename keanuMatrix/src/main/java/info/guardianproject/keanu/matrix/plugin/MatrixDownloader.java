package info.guardianproject.keanu.matrix.plugin;


import android.util.Log;
import android.webkit.URLUtil;

import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import info.guardianproject.iocipher.File;
import info.guardianproject.keanu.core.util.SecureMediaStore;

public class MatrixDownloader {


    private String mMimeType = null;

    public MatrixDownloader()
    {}

    public boolean get (String urlString, OutputStream storageStream) throws IOException
    {
        try {

            final URL url = new URL(urlString);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            //wait up to 60 seconds
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);

            InputStream inputStream = connection.getInputStream();
            connection.connect();

            mMimeType = connection.getContentType();
            int contentLength = connection.getContentLength();

            if (mMimeType != null) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = inputStream.read(buffer)) != -1) {
                    storageStream.write(buffer, 0, count);
                }
                storageStream.flush();
                storageStream.close();
                connection.disconnect();

                return true;
            }
            else {
                connection.disconnect();
                storageStream.close();
                return false;
            }

        } catch (Exception e) {

            Log.d("Download","Error downloading media",e);
            return false;
        }
    }

    public String getMimeType ()
    {
        return mMimeType;
    }

    public File openSecureStorageFile(String sessionId, String url) throws FileNotFoundException {
//        debug( "openFile: url " + url) ;

        String filename = getFilenameFromUrl(url);
        String localFilename = SecureMediaStore.getDownloadFilename(sessionId, filename);
        //  debug( "openFile: localFilename " + localFilename) ;
        info.guardianproject.iocipher.File fileNew = new info.guardianproject.iocipher.File(localFilename);
        fileNew.getParentFile().mkdirs();

        return fileNew;
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
}