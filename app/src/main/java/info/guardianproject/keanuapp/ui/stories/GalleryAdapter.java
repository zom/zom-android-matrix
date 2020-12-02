package info.guardianproject.keanuapp.ui.stories;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.lang.ref.WeakReference;

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
            int titleColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.TITLE);
            int displayColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);


            if (uriColumn >= 0 && typeColumn >= 0) {
                String data = cursor.getString(uriColumn);

                // We know it's a file uri here, it's from MediaStore.Files
                File fileData = new File(data);
                Uri uri = Uri.fromFile(fileData);
                int mediaType = cursor.getInt(typeColumn);
                String mimeType = (mimeTypeColumn >= 0) ? cursor.getString(mimeTypeColumn) : "";
                String displayName = cursor.getString(titleColumn);

                if (TextUtils.isEmpty(displayName))
                    displayName = cursor.getString(displayColumn);

                if (TextUtils.isEmpty(displayName))
                    displayName = fileData.getName();

                long id = getItemId(cursor.getPosition());

                if (mimeType.equalsIgnoreCase("application/pdf")) {
                    holder.imageView.setImageResource(R.drawable.ic_pdf_24dp);
                    new ThumbnailLoader(context, holder, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE, uri, id).execute();
                } else {
                    switch (mediaType) {
                        case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
                            new ThumbnailLoader(context, holder, mediaType, uri, id).execute();
                            break;
                        case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
                            new ThumbnailLoader(context, holder, mediaType, uri, id).execute();
                            break;

                        case MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO:
                            holder.imageView.setImageResource(R.drawable.ic_audiotrack_white_24dp);
                            new ThumbnailLoader(context, holder, mediaType, uri, id).execute();
                            break;
                    }
                }

                holder.titleView.setText(displayName);

                if (this.mediaType == GalleryAdapter.MediaType.Pdf || this.mediaType == MediaType.Audio)
                    holder.titleView.setVisibility(View.VISIBLE);
                else
                    holder.titleView.setVisibility(View.GONE);

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            Log.v("onMediaItemClicked","onMediaItemClicked=="+uri);
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

    private class LoadCursorTask extends AsyncTask<Void, Void, Cursor> {
        private Cursor cursor = null;

        LoadCursorTask() {
        }

        @Override
        protected Cursor doInBackground(Void... values) {
            try {
                ContentResolver cr = context.getContentResolver();
                Uri uri = MediaStore.Files.getContentUri("external");

                String[] projection = new String[]{
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DATA,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.TITLE
                };

                String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";

                String selection = null;
                String[] selectionArgs = null;

                switch (mediaType) {
                    case Pdf:
                        selection = MediaStore.Files.FileColumns.MIME_TYPE + " = ?";
                        selectionArgs = new String[]{"application/pdf"};
                        break;
                    case Audio:
                        selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " = " + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
                        break;
                    case Image:
                        selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " = " + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
                        break;
                    case Video:
                        selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " = " + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                        break;
                    default:
                        selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " != " + MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
                        selection += " OR " + MediaStore.Files.FileColumns.MIME_TYPE + " = ?";
                        selectionArgs = new String[]{"Application/PDF"};
                        break;
                }

                return cr.query(uri, projection, selection, selectionArgs, sortOrder);
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
        protected void onPostExecute(Cursor cursor) {
            synchronized (GalleryAdapter.this) {
                if (loadCursorTask == this) {
                    loadCursorTask = null;
                } else {
                    // We are not the current task anymore(?)
                    if (cursor != null) {
                        cursor.close();
                        Log.v(LOGTAG, "LoadCursorTask: crsor close finished");
                    }
                    return;
                }
                if (LOGGING)
                    Log.v(LOGTAG, "LoadCursorTask: finished");
                changeCursor(cursor);
            }
        }
    }

    private static class ThumbnailLoader extends AsyncTask<Void, Void, Bitmap> {
        private final int mediaType;
        private final Uri uri;
        private long id;
        WeakReference<Context> _context;
        WeakReference<GalleryViewHolder> _owner;

        ThumbnailLoader(Context context, GalleryViewHolder owner, int mediaType, Uri uri, long id) {
            _context = new WeakReference<Context>(context);
            _owner = new WeakReference<GalleryViewHolder>(owner);
            this.mediaType = mediaType;
            this.uri = uri;
            this.id = id;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            switch (mediaType) {
                case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO: {
                    Context context = _context.get();
                    if (context != null) {
                        return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                }
                break;
                case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE: {
                    Context context = _context.get();
                    if (context != null) {
                        return MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                }
                break;
                case MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO: {
                    Context context = _context.get();
                    if (context != null) {

                        if (uri.getScheme().equals("content")) {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();

                            byte[] rawArt;
                            BitmapFactory.Options bfo = new BitmapFactory.Options();

                            mmr.setDataSource(context, uri);
                            rawArt = mmr.getEmbeddedPicture();
                            if (null != rawArt) {
                                return BitmapFactory.decodeByteArray(rawArt, 0, rawArt.length, bfo);
                            }
                        }

                    }
                }
                break;
                case MediaStore.Files.FileColumns.MEDIA_TYPE_NONE: {
                    Context context = _context.get();
                    GalleryViewHolder holder = _owner.get();
                    if (context != null && holder != null) {
                        int width = holder.imageView.getWidth();
                        int height = holder.imageView.getHeight();
                        if (width == 0) {
                            width = 512;
                        }
                        if (height == 0) {
                            height = 384;
                        }
                        try {
                            Bitmap thumb = null;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                                thumb = DocumentsContract.getDocumentThumbnail(context.getContentResolver(), uri, new Point(width, height), null);
                            }
                            return thumb;
                        } catch (Exception ignored) {}
                    }

                }
                break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            GalleryViewHolder owner = _owner.get();
            if (owner != null) {
                if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ||
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_NONE) {
                    if (result != null) {
                        owner.imageView.setImageBitmap(result);
                    }
                } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                    if (result == null) {
                        Context context = _context.get();
                        if (context != null) {
                            GlideUtils.loadImageFromUri(context, uri, owner.imageView);
                        }
                    } else {
                        owner.imageView.setImageBitmap(result);
                    }
                } else {
                    owner.imageView.setImageBitmap(result);
                }
            }
        }
    }
}