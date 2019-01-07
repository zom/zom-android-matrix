package info.guardianproject.keanu.core.util;

public interface UploadProgressListener {

    public void onUploadProgress(long sent, long total);
}
