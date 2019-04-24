package info.guardianproject.keanuapp.ui.stories;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;

import info.guardianproject.keanuapp.R;

public class GalleryViewHolder extends RecyclerView.ViewHolder {
    final ImageView imageView;

    public GalleryViewHolder(View itemView) {
        super(itemView);
        this.imageView = (ImageView) itemView.findViewById(R.id.imageView);
    }
}
