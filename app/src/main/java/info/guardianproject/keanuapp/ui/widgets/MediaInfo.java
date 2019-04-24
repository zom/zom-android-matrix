package info.guardianproject.keanuapp.ui.widgets;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import info.guardianproject.keanu.core.model.AddressParcelHelper;
import info.guardianproject.keanuapp.ui.conversation.AddUpdateMediaActivity;

/**
 * Created by N-Pex on 2019-04-17.
 */
public class MediaInfo extends Object implements Parcelable {
    public final Uri uri;
    public final String mimeType;

    public MediaInfo(Uri uri, String mimeType) {
        this.uri = uri;
        this.mimeType = mimeType;
    }

    public MediaInfo(Parcel source) {
        this.uri = Uri.parse(source.readString());
        this.mimeType = source.readString();
    }

    public boolean isImage() {
        return TextUtils.isEmpty(mimeType) || mimeType.startsWith("image/");
    }

    public boolean isAudio() {
        return !TextUtils.isEmpty(mimeType) && mimeType.startsWith("audio/");
    }

    public boolean isVideo() {
        return !TextUtils.isEmpty(mimeType) && mimeType.startsWith("video/");
    }

    public boolean isPDF() {
        return !TextUtils.isEmpty(mimeType) && mimeType.contentEquals("application/pdf");
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uri.toString());
        dest.writeString(mimeType);
    }

    public int describeContents() {
        return 0;
    }

    public static final Creator<MediaInfo> CREATOR = new Creator<MediaInfo>() {
        public MediaInfo createFromParcel(Parcel source) {
            return new MediaInfo(source);
        }

        public MediaInfo[] newArray(int size) {
            return new MediaInfo[size];
        }
    };
}