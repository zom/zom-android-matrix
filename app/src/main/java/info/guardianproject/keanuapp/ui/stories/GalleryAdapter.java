package info.guardianproject.keanuapp.ui.stories;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.widgets.CursorRecyclerViewAdapter;
import info.guardianproject.keanuapp.ui.widgets.GlideUtils;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;

/**
 * Created by N-Pex on 2019-04-12.
 */
public class GalleryAdapter extends CursorRecyclerViewAdapter<GalleryViewHolder> {
    public static final String LOGTAG = "GalleryAdapter";
    public static final boolean LOGGING = true;

    public interface GalleryAdapterListener {
        void onMediaItemClicked(MediaInfo media);
    }

    public enum MediaType {
        All,
        Pdf,
        Image,
        Video,
        Audio
    }

    private final Context context;
    private final GalleryAdapterListener listener;
    private MediaType mediaType = MediaType.All;
    private LoadCursorTask loadCursorTask;

    public GalleryAdapter(Context context, GalleryAdapterListener listener) {
        super(context, null);
        this.context = context;
        this.listener = listener;
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
            int mimeTypeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
            if (uriColumn >= 0 && typeColumn >= 0) {
                String data = cursor.getString(uriColumn);

                // We know it's a file uri here, it's from MediaStore.Files
                Uri uri = Uri.fromFile(new File(data));
                int mediaType = cursor.getInt(typeColumn);
                String mimeType = (mimeTypeColumn >= 0) ? cursor.getString(mimeTypeColumn) : "";
                switch (mediaType) {
                    case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
                        holder.imageView.setImageBitmap(ThumbnailUtils.createVideoThumbnail(
                                uri.getPath(), MediaStore.Video.Thumbnails.MINI_KIND));
                        break;
                    case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
                        GlideUtils.loadImageFromUri(context, uri, holder.imageView);
                        break;

                    case MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO:
                        holder.imageView.setImageResource(R.drawable.ic_audiotrack_white_24dp);
                        break;
                }

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onMediaItemClicked(new MediaInfo(uri, mimeType));
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onViewRecycled(@NonNull GalleryViewHolder holder) {
        super.onViewRecycled(holder);
        holder.imageView.setImageDrawable(null);
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