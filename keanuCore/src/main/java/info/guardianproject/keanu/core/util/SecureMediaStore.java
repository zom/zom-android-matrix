/**
 *
 */
package info.guardianproject.keanu.core.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.util.UUID;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;


/**
 * Copyright (C) 2014 Guardian Project.  All rights reserved.
 *
 * @author liorsaar
 *
 */
public class SecureMediaStore {

    public static final String TAG = SecureMediaStore.class.getName();
   // private static String dbFilePath;
//    private static final String LEGACY_BLOB_NAME = "keanumedia.db";
    private static final String BLOB_NAME = "keanumedia.db";

    public static final int DEFAULT_IMAGE_WIDTH = 1080;
    private final static String LOG_TAG = "SecureMediaStore";

    private static final String ReservedChars = "[|\\?*<\":>+/']";

    public static void unmount() {
        VirtualFileSystem.get().unmount();
    }

    public static void list(String parent) {
        File file = new File(parent);
        File[] list = file.listFiles();

        Log.d(TAG, "Dir=" + file.isDirectory() + ";" + file.getAbsolutePath() + ";files=" + list.length);

        for (File fileChild : list)
        {
            if (fileChild.isDirectory()) {
                list(fileChild.getAbsolutePath());
            } else {
                Log.d(TAG,  fileChild.getAbsolutePath()+ " (" + fileChild.length()/1000 + "KB)");
            }
        }
    }

    public static void deleteLargerThan( long fileLengthFilter ) throws IOException {
        String dirName = "/";
        File file = new File(dirName);
        // if the session doesnt have any ul/dl files - bail
        if (!file.exists()) {
            return;
        }
        // delete recursive
        deleteBySize( file, fileLengthFilter );
    }

    public static void deleteSession( String sessionId ) throws IOException {
        String dirName = "/" + sessionId;
        File file = new File(dirName);
        // if the session doesnt have any ul/dl files - bail
        if (!file.exists()) {
            return;
        }
        // delete recursive
        delete( dirName );
    }

    private static void deleteBySize(File parent, long sizeFilter) throws IOException {
        // if a file or an empty directory - delete it

        if (parent.length() > sizeFilter) {
            //    Log.e(TAG, "delete:" + parent );
            if (!parent.delete()) {
                throw new IOException("Error deleting " + parent);
            }
            return;
        }

        // directory - recurse
        File[] list = parent.listFiles();
        for (File fileToDelete : list) {
            deleteBySize( fileToDelete, sizeFilter );
        }

    }

    private static void delete(String parentName) throws IOException {
        File parent = new File(parentName);
        // if a file or an empty directory - delete it
        if (!parent.isDirectory()  ||  parent.list().length == 0 ) {
        //    Log.e(TAG, "delete:" + parent );
            if (!parent.delete()) {
                throw new IOException("Error deleting " + parent);
            }
            return;
        }
        // directory - recurse
        String[] list = parent.list();
        for (int i = 0 ; i < list.length ; i++) {
            String childName = parentName + "/" + list[i];
            delete( childName );
        }
        delete( parentName );
    }

    private static final String VFS_SCHEME = "vfs";
    private static final String CONTENT_SCHEME = "content";
    private static final String ENCODING = "UTF-8";

    public static Uri vfsUri(String filename) throws UnsupportedEncodingException {
        return Uri.parse(VFS_SCHEME + ":" + filename);
    }

    public static boolean isVfsUri(Uri uri) {
        return TextUtils.equals(VFS_SCHEME, uri.getScheme());
    }

    public static boolean isContentUri(Uri uri) {
        return TextUtils.equals(CONTENT_SCHEME, uri.getScheme());
    }

    public static boolean isContentUri(String uriString) {
        if (TextUtils.isEmpty(uriString))
            return false;
        else
            return uriString.startsWith(CONTENT_SCHEME + ":/");
    }


    public static boolean isVfsUri(String uriString) {
        if (TextUtils.isEmpty(uriString))
            return false;
        else
            return uriString.startsWith(VFS_SCHEME + ":/");
    }

    public static Bitmap getThumbnailVfs(Uri uri, int thumbnailSize) {
        
        if (!VirtualFileSystem.get().isMounted())
            return null;
        
        File image = new File(uri.getPath());

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inInputShareable = true;
        options.inPurgeable = true;

        try {
            FileInputStream fis = new FileInputStream(new File(image.getPath()));
            BitmapFactory.decodeStream(fis, null, options);
        } catch (Exception e) {
            LogCleaner.warn(LOG_TAG,"unable to read vfs thumbnail" + e.toString());
            return null;
        }

        if ((options.outWidth == -1) || (options.outHeight == -1))
            return null;

        int originalSize = (options.outHeight > options.outWidth) ? options.outHeight
                : options.outWidth;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = originalSize / thumbnailSize;

        try {
            FileInputStream fis = new FileInputStream(new File(image.getPath()));
            Bitmap scaledBitmap = BitmapFactory.decodeStream(fis, null, opts);
            return scaledBitmap;
        } catch (FileNotFoundException e) {
            LogCleaner.warn(LOG_TAG, "can't find IOcipher file: " + image.getPath());
            return null;
        }
        catch (OutOfMemoryError oe)
        {
            LogCleaner.error(LOG_TAG, "out of memory loading thumbnail: " + image.getPath(), oe);

            return null;
        }
    }


    /**
     * Careful! All of the {@code File}s in this method are {@link java.io.File}
     * not {@link info.guardianproject.iocipher.File}s
     *
     * @param context
     * @param key
     * @throws IllegalArgumentException
     */
    public synchronized static void init(Context context, byte[] key) throws IllegalArgumentException {
        // there is only one VFS, so if its already mounted, nothing to do
        VirtualFileSystem vfs = VirtualFileSystem.get();

        if (vfs.isMounted()) {
            Log.w(TAG, "VFS " + vfs.getContainerPath() + " is already mounted, so unmount()");
            /**
            try
            {
                vfs.unmount();
            }
            catch (Exception e)
            {
                Log.w(TAG, "VFS " + vfs.getContainerPath() + " issues with unmounting: " + e.getMessage());
            }**/
            return;
        }

        Log.w(TAG,"Mounting VFS: " + vfs.getContainerPath());

        java.io.File fileDb = new java.io.File(getInternalDbFilePath(context));

        //TODO check if moving from v3 to v4 and if so 'migrate cipher'
        //checkUpgrade(context, fileDb, key);

        try {
            if (!fileDb.exists()) {
                fileDb.getParentFile().mkdirs();
                vfs.setContainerPath(fileDb.getAbsolutePath());
                vfs.createNewContainer(key);
            }

            if (!vfs.isMounted())
                vfs.mount(fileDb.getAbsolutePath(), key);

        }
        catch (Exception e)
        {
            Log.w(TAG, "VFS " + vfs.getContainerPath() + " issues with mounting: " + e.getMessage());
        }

       // deleteLegacy (context);

    }

    /**
    public static void checkUpgrade (Context context, File fileDb, byte[] key)
    {

         java.io.File fileLegacy = new java.io.File(getLegacyDbFilePath(context));

        Log.d(TAG,"legacy db: " + fileDb.getAbsolutePath() + " size=" + fileDb.length());

        if (fileLegacy.exists())
        {

            final boolean[] status = {false,false};
            char[] keyString = SQLCipherOpenHelper.encodeRawKey(key,true);

            final SQLiteDatabase db= SQLiteDatabase.openDatabase(fileLegacy.getAbsolutePath(), keyString, null,SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY,
                    new SQLiteDatabaseHook() {
                        @Override
                        public void preKey(SQLiteDatabase database) {

                        }

                        @Override
                        public void postKey(SQLiteDatabase database) {
                            Boolean migrationOccurred = false;


                            database.rawQuery("PRAGMA cipher_page_size = 8192;", new String[]{});


                            Cursor c = database.rawQuery("PRAGMA cipher_migrate", null);


                            if (c.getCount() == 1) {
                                c.moveToFirst();
                                String selection = c.getString(0);

                                migrationOccurred = selection.equals("0");

                                Log.d(TAG,"selection: " + selection);
                            }

                            c.close();

                                    Log.d(TAG,"migrationOccurred: " + migrationOccurred);


                            //database.rawQuery("PRAGMA journal_mode = WAL;", new String[]{});
                            //database.rawQuery("PRAGMA synchronous = NORMAL;", new String[]{});


                            if (database.isOpen()) {

                                database.rawQuery("ATTACH DATABASE \""
                                        + fileDb.getAbsolutePath() + "\" AS sqlcipher4 KEY \"" + keyString + "\";", new String[]{});
                                database.rawQuery("SELECT sqlcipher_export('sqlcipher4');", new String[]{});
                                database.rawQuery("DETACH DATABASE sqlcipher4;", new String[]{});

                                if (fileDb.exists())
                                {
                                  //  fileLegacy.delete();
                                    Log.d(TAG,"new db: " + fileDb.getAbsolutePath() + " size=" + fileDb.length());

                                }


                            }


                            status[1] = true;

                        }
                    }, new DatabaseErrorHandler() {
                        @Override
                        public void onCorruption(SQLiteDatabase dbObj) {

                            Log.e(TAG,"database corrupted: v" + dbObj.getVersion());
                            //set upgrade as done
                            status[1] = true;
                            Runtime.getRuntime().exit(-4242); //db is corrupted
                        }
                    });

            while (!status[1])
            {
                try { Thread.sleep(1000);}
                catch (Exception e){}
            }

            if (db != null && db.isOpen())
                db.close();

        }


    }**/

    /**
    public static void checkUpgrade (Context context, byte[] key, String dbFilePath)
    {

        final String IOCIPHER_VERSION_KEY = "ioc.vers";

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getString(IOCIPHER_VERSION_KEY,"v3").equals("v3"))
        {

            final boolean[] status = {false,false};

                final SQLiteDatabase db= SQLiteDatabase.openDatabase(dbFilePath, "", null, SQLiteDatabase.OPEN_READWRITE,
                    new SQLiteDatabaseHook() {
                        @Override
                        public void preKey(SQLiteDatabase database) {

                        }

                        @Override
                        public void postKey(SQLiteDatabase database) {

                            String keyString = SQLCipherOpenHelper.encodeRawKeyToStr(key);
                            database.rawQuery("PRAGMA key = '" + keyString + "';", new String[]{});
                        //    database.execSQL("PRAGMA cipher_compatibility = 3");
                            database.rawQuery("PRAGMA cipher_page_size = 8192;", new String[]{});
                            database.rawQuery("PRAGMA journal_mode = WAL;", new String[]{});
                            database.rawQuery("PRAGMA synchronous = NORMAL;", new String[]{});

                            if (database.isOpen()) {
                                Cursor cursor = database.rawQuery("PRAGMA cipher_migrate", new String[]{});
                                String value = "";
                                if (cursor != null) {
                                    cursor.moveToFirst();
                                    value = cursor.getString(0);
                                    cursor.close();
                                }
                                status[0] = Integer.valueOf(value) == 0;

                                if (status[0]) {
                                    prefs.edit().putString(IOCIPHER_VERSION_KEY, "v4").commit();
                                }

                                //set upgrade as done
                                status[1] = true;
                            }
                        }
                    }, new DatabaseErrorHandler() {
                        @Override
                        public void onCorruption(SQLiteDatabase dbObj) {

                            Log.e(TAG,"database corrupted: v" + dbObj.getVersion());
                            //set upgrade as done
                            status[1] = true;
                            Runtime.getRuntime().exit(-4242); //db is corrupted
                        }
                    });

            while (!status[1])
            {
                try { Thread.sleep(1000);}
                catch (Exception e){}
            }

            if (db != null)
                db.close();
        }
    }**/


    /**
    public static void deleteLegacy (Context context)
    {
        File fileLegacy = new File(getLegacyDbFilePath(context));
        if (fileLegacy.exists())
            fileLegacy.delete();

    }**/

    public static boolean isMounted ()
    {
        return VirtualFileSystem.get().isMounted();
    }

    /**
     * get the internal storage path for the chat media file storage file.
     */
    public static String getInternalDbFilePath(Context c) {

        return c.getFilesDir() + "/" + BLOB_NAME;
    }


    /**
    public static String getLegacyDbFilePath(Context c) {
        return c.getFilesDir() + "/" + LEGACY_BLOB_NAME;
    }**/

    /**
     * Copy device content into vfs.
     * All imported content is stored under /SESSION_NAME/
     * The original full path is retained to facilitate browsing
     * The session content can be deleted when the session is over
     * @param sourceFile
     * @return vfs uri
     * @throws IOException
     */
    public static Uri importContent(String sessionId, java.io.File sourceFile) throws IOException {
        //list("/");
        String targetPath = "/" + sessionId + "/upload/" + UUID.randomUUID().toString() + "/" + sourceFile.getName();
        targetPath = createUniqueFilename(targetPath);
        copyToVfs( sourceFile, targetPath );
        //list("/");
        return vfsUri(targetPath);
    }

    /**
     * Copy device content into vfs.
     * All imported content is stored under /SESSION_NAME/
     * The original full path is retained to facilitate browsing
     * The session content can be deleted when the session is over
     * @param sessionId
     * @return vfs uri
     * @throws IOException
     */
    public static Uri importContent(String sessionId, String fileName, InputStream sourceStream) throws IOException {
        //list("/");
       // Log.v("ImageSend","upload_5==");
        String targetPath = "/" + sessionId + "/upload/" + UUID.randomUUID().toString() + '/' + fileName;
        targetPath = createUniqueFilename(targetPath);
        copyToVfs( sourceStream, targetPath );
        //list("/");
        return vfsUri(targetPath);
    }

    /**
     * Copy device content into vfs.
     * All imported content is stored under /SESSION_NAME/
     * The original full path is retained to facilitate browsing
     * The session content can be deleted when the session is over
     * @param sessionId
     * @return vfs uri
     * @throws IOException
     */
    public static Uri createContentPath(String sessionId, String fileName) throws IOException {
        //list("/");
        String targetPath = "/" + sessionId + "/upload/" + UUID.randomUUID().toString() + '/' + fileName;
        targetPath = createUniqueFilename(targetPath);
        mkdirs( targetPath );

        //list("/");
        return vfsUri(targetPath);
    }

    public static Uri resizeAndImportImage(Context context, String sessionId, Uri uri, String mimeType)
            throws IOException {

        return resizeAndImportImage(context, sessionId, uri, mimeType, DEFAULT_IMAGE_WIDTH);
    }

        /**
         * Resize an image to an efficient size for sending via OTRDATA, then copy
         * that resized version into vfs. All imported content is stored under
         * /SESSION_NAME/ The original full path is retained to facilitate browsing
         * The session content can be deleted when the session is over
         *
         * @param sessionId
         * @return vfs uri
         * @throws IOException
         */
    public static Uri resizeAndImportImage(Context context, String sessionId, Uri uri, String mimeType, int maxImageSize)
            throws IOException {

        String originalImagePath = uri.getPath();
        String targetPath = "/" + sessionId + "/upload/" + UUID.randomUUID().toString() + "/image";
        boolean savePNG = false;

        if (originalImagePath.endsWith(".png") || (mimeType != null && mimeType.contains("png"))
                || originalImagePath.endsWith(".gif") || (mimeType != null && mimeType.contains("gif"))
                ) {
            savePNG = true;
            targetPath += ".png";
        }
        else
        {
            targetPath += ".jpg";


        }

        //load lower-res bitmap
        Bitmap bmp = getThumbnailFile(context, uri, maxImageSize);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (savePNG)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
        else
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);

        info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(targetPath);
        file.getParentFile().mkdirs();
        FileOutputStream out = new info.guardianproject.iocipher.FileOutputStream(file);
        IOUtils.write(baos.toByteArray(),out);
        out.close();        
        bmp.recycle();        

        return vfsUri(targetPath);
    }

    public static InputStream openInputStream (Context context, Uri uri) throws FileNotFoundException {
        InputStream is;

        if (uri.getScheme() != null && uri.getScheme().equals("vfs"))
            is = new info.guardianproject.iocipher.FileInputStream(uri.getPath());
        else
            is = context.getContentResolver().openInputStream(uri);


        return is;
    }
    public static Bitmap getThumbnailFile(Context context, Uri uri, int thumbnailSize) throws IOException {

        InputStream is = openInputStream(context, uri);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inInputShareable = true;
        options.inPurgeable = true;
        
        BitmapFactory.decodeStream(is, null, options);
        
        if ((options.outWidth == -1) || (options.outHeight == -1))
            return null;

        int originalSize = (options.outHeight > options.outWidth) ? options.outHeight
                : options.outWidth;

        is.close();
        is = openInputStream(context, uri);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(options, thumbnailSize, thumbnailSize);

        Bitmap scaledBitmap = BitmapFactory.decodeStream(is, null, opts);
        is.close();

        is = openInputStream(context,uri);
        ExifInterface exif = new ExifInterface(is);
        int orientationType = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int orientationD  = 0;
        if (orientationType == ExifInterface.ORIENTATION_ROTATE_90)
            orientationD = 90;
        else if (orientationType == ExifInterface.ORIENTATION_ROTATE_180)
            orientationD = 180;
        else if (orientationType == ExifInterface.ORIENTATION_ROTATE_270)
            orientationD = 270;

        is.close();

        if (orientationD != 0)
            scaledBitmap = rotateBitmap(scaledBitmap, orientationD);

        return scaledBitmap;
    }

    public static void exportAll(String sessionId ) throws IOException {
    }

    public static void exportContent(String mimeType, Uri mediaUri, java.io.File exportPath) throws IOException {
        String sourcePath = mediaUri.getPath();

        copyToExternal( sourcePath, exportPath);
    }

    public static java.io.File exportPath(String mimeType, Uri mediaUri) {
        java.io.File targetFilename;
        if (mimeType.startsWith("image")) {
            targetFilename = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),mediaUri.getLastPathSegment());
        } else if (mimeType.startsWith("audio")) {
            targetFilename = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),mediaUri.getLastPathSegment());
        } else {
            targetFilename = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),mediaUri.getLastPathSegment());
        }
        java.io.File targetUniqueFilename = createUniqueFilenameExternal(targetFilename);
        return targetFilename;
    }

    public static void copyToVfs(String sourcePath, String targetPath) throws IOException {
       copyToVfs(new java.io.File(sourcePath), targetPath);
    }

    public static void copyToVfs(java.io.File sourceFile, String targetPath) throws IOException {

        File fileOut = new info.guardianproject.iocipher.File(targetPath);
        fileOut.getParentFile().mkdirs();

        InputStream fis  = new java.io.FileInputStream(sourceFile);

        FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut, false);

        int read;
        byte[] data = new byte[4096];
        while(-1 != (read = fis.read(data))) {

            byte[] dataToWrite = new byte[read];
            System.arraycopy(data,0,dataToWrite,0,read);
            fos.write(data);
        }

        fos.close();
    }


    public static void copyToVfs(InputStream sourceIS, String targetPath) throws IOException {
        // create the target directories tree
        File fileOut = new info.guardianproject.iocipher.File(targetPath);
        fileOut.getParentFile().mkdirs();

        // copy
        OutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut, false);

        int read;
        byte[] data = new byte[4096];
        while(-1 != (read = sourceIS.read(data))) {

            byte[] dataToWrite = new byte[read];
            System.arraycopy(data,0,dataToWrite,0,read);
            fos.write(data);
        }

        fos.close();
    }


    public static void copyToVfs(byte buf[], String targetPath) throws IOException {
        File file = new File(targetPath);
        FileOutputStream out = new FileOutputStream(file);
        IOUtils.write(buf,out);
        out.close();
    }
    

    public static void copyToExternal(String sourcePath, java.io.File targetPath) throws IOException {
        // copy
        FileInputStream fis = new FileInputStream(new File(sourcePath));
        java.io.FileOutputStream fos = new java.io.FileOutputStream(targetPath, false);

        IOUtils.copy(fis, fos);

        fos.close();
        fis.close();
    }

    private static void mkdirs(String targetPath) throws IOException {
        File targetFile = new File(targetPath);
        if (!targetFile.exists()) {
            File dirFile = targetFile.getParentFile();
            if (!dirFile.exists()) {
                boolean created = dirFile.mkdirs();
                if (!created) {
                    throw new IOException("Error creating " + targetPath);
                }
            }
        }
    }

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static boolean sessionExists(String sessionId) {
        return exists( "/" + sessionId );
    }

    private static String createUniqueFilename( String filename ) {

        if (!exists(filename)) {
            return filename;
        }
        int count = 1;
        String uniqueName;
        File file;
        do {
            uniqueName = formatUnique(filename, count++);
            file = new File(uniqueName);
        } while(file.exists());

        return uniqueName;
    }

    private static String formatUnique(String filename, int counter) {
        int lastDot = filename.lastIndexOf(".");
        if (lastDot != -1)
        {
            String name = filename.substring(0,lastDot);
            String ext = filename.substring(lastDot);
            return name + "-" + counter + "." + ext;
        }
        else
        {
            return filename + counter;
        }
    }

    public static String getDownloadFilename(String sessionId, String filenameFromUrl) {
        String filename = "/" + sessionId + "/download/" + filenameFromUrl.replaceAll(ReservedChars, "_");
        String uniqueFilename = createUniqueFilename(filename);
        return uniqueFilename;
    }

    private static java.io.File createUniqueFilenameExternal(java.io.File filename ) {
        if (!filename.exists()) {
            return filename;
        }
        int count = 1;
        String uniqueName;
        java.io.File file;
        do {
            uniqueName = formatUnique(filename.getName(), count++);
            file = new java.io.File(filename.getParentFile(),uniqueName);
        } while(file.exists());

        return file;
    }

    public static int getImageOrientation(String imagePath) {
        int rotate = 0;
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rotate;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int rotate) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return rotatedBitmap;
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private static void copyFile(java.io.File src, java.io.File dst) throws IOException {

        FileChannel inChannel = null;
        FileChannel outChannel = null;

        try {
            inChannel = new java.io.FileInputStream(src).getChannel();
            outChannel = new java.io.FileOutputStream(dst).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

}
