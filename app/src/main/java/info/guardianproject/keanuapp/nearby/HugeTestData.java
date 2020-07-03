package info.guardianproject.keanuapp.nearby;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class HugeTestData {

    public final String data;

    public HugeTestData(Context context) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int bytesRead;

        try (InputStream is = context.getAssets().open("LICENSE_OFL.txt")) { //"Lato-Regular.ttf")) {
            while ((bytesRead = is.read(b)) > 0) {
                buffer.write(b, 0, bytesRead);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        data = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
    }
}
