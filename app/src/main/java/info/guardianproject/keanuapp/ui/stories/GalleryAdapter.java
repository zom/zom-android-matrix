package info.guardianproject.keanuapp.ui.stories;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.widgets.CursorRecyclerViewAdapter;

/**
 * Created by N-Pex on 2019-04-12.
 */
public class GalleryAdapter extends CursorRecyclerViewAdapter<GalleryViewHolder> {
    public static final String LOGTAG = "GalleryAdapter";
    public static final boolean LOGGING = true;

    public enum MediaType {
        All,
        Pdf,
        Image,
        Video,
        Audio
    }

    private final Context context;
    private MediaType mediaType = MediaType.All;
    private LoadCursorTask loadCursorTask;

    public GalleryAdapter(Context context) {
        super(context, null);
        this.context = context;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
        loadCursor();
    }

    @Override
    public GalleryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.story_gallery_item, parent, false);
        return new GalleryViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public void onBindViewHolder(GalleryViewHolder holder, Cursor cursor) {
        try {
            int uriColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            int typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE);
            if (uriColumn >= 0 && typeColumn >= 0) {
                String data = cursor.getString(uriColumn);
                int mediaType = cursor.getInt(typeColumn);
                switch (mediaType) {
                    case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
                        String mUri = getRealPathFromURI(context, Uri.parse(data), "VIDEO");
                        holder.imageView.setImageBitmap(ThumbnailUtils.createVideoThumbnail(
                                mUri, MediaStore.Video.Thumbnails.MINI_KIND));
                        break;
                    case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
                        holder.imageView.setImageURI(Uri.parse(data));
                        break;

                    case MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO:
                        holder.imageView.setImageResource(R.drawable.ic_audiotrack_white_24dp);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getRealPathFromURI(Context context,Uri contentURI,String type) {

        String result  = null;
        try {
            Cursor cursor = context.getContentResolver().query(contentURI, null, null, null, null);
            if (cursor == null) { // Source is Dropbox or other similar local file path
                result = contentURI.getPath();
                Log.d("TAG", "result******************" + result);
            } else {
                cursor.moveToFirst();
                int idx = 0;
                if(type.equalsIgnoreCase("IMAGE")){
                    idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                }else if(type.equalsIgnoreCase("VIDEO")){
                    idx = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATA);
                }else if(type.equalsIgnoreCase("AUDIO")){
                    idx = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);
                }
                result = cursor.getString(idx);
                Log.d("TAG", "result*************else*****" + result);
                cursor.close();
            }
        } catch (Exception e){
            Log.e("TAG", "Exception ",e);
        }
        return result;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        loadCursor();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        synchronized (this) {
            changeCursor(null);
        }
    }

    private void loadCursor() {
        if (loadCursorTask != null) {
            if (LOGGING)
                Log.d(LOGTAG, "Cancel load cursor task");
            loadCursorTask.cancel(true);
        }
        loadCursorTask = new LoadCursorTask();
        loadCursorTask.execute();
    }

    private class LoadCursorTask extends AsyncTask<Void, Void, Cursor>
    {
        private Cursor cursor = null;

        LoadCursorTask()
        {
        }

        @Override
        protected Cursor doInBackground(Void... values)
        {
            try {
                ContentResolver cr = context.getContentResolver();
                Uri uri = MediaStore.Files.getContentUri("external");

                String[] projection = null; // TODO - only use columns we need!!!

                String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
                String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " != " + MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
                selection += " OR " + MediaStore.Files.FileColumns.MIME_TYPE + " = ?";
                String[] selectionArgs = new String[] {"Application/PDF"};

                Cursor cursor = cr.query(uri, projection, selection, selectionArgs, sortOrder);
                return cursor;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (cursor != null) {
                if (LOGGING)
                    Log.d(LOGTAG, "Cancelled - Close cursor " + cursor.hashCode());
                cursor.close();
                cursor = null;
            }
        }

        @Override
        protected void onPostExecute(Cursor cursor)
        {
            synchronized (GalleryAdapter.this) {
                if (loadCursorTask == this) {
                    loadCursorTask = null;
                } else {
                    // We are not the current task anymore(?)
                    if (cursor != null) {
                        cursor.close();
                    }
                    return;
                }
                if (LOGGING)
                    Log.v(LOGTAG, "LoadCursorTask: finished");
                changeCursor(cursor);
            }
        }
    }
}