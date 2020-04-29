package info.guardianproject.keanuapp.ui.stories;

import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.RecyclerView;

import info.guardianproject.keanuapp.R;

public class MultiSelectGalleryViewHolder extends RecyclerView.ViewHolder {
    final ImageView imageView;
    final AppCompatCheckBox checkBox;

    public MultiSelectGalleryViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = (ImageView)itemView.findViewById(R.id.imageView);
        checkBox = (AppCompatCheckBox)itemView.findViewById(R.id.checkbox);

    }
}
