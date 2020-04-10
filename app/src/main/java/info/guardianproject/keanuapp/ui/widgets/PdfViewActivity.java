package info.guardianproject.keanuapp.ui.widgets;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImUrlActivity;
import info.guardianproject.keanuapp.R;

public class PdfViewActivity extends AppCompatActivity {


    private boolean mShowResend = false;
    private Uri mMediaUri = null;
    private String mMimeType = null;
    private PDFView mPdfView = null;

    private String mMessageId = null;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

       // getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
       // supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
       // getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        mShowResend = getIntent().getBooleanExtra("showResend",false);

        //setContentView(R.layout.image_view_activity);
        getSupportActionBar().show();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setTitle("");

        setContentView(R.layout.activity_pdf_viewer);

        mPdfView = findViewById(R.id.pdfView);

        mMediaUri = getIntent().getData();
        mMimeType = getIntent().getType();

        InputStream is;

        if (mMediaUri.getScheme() == null || mMediaUri.getScheme().equals("vfs"))
        {
            try {
                is = (new info.guardianproject.iocipher.FileInputStream(mMediaUri.getPath()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }
        else
        {
            try {
                is = (getContentResolver().openInputStream(mMediaUri));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }


        mPdfView.fromStream(is)
            .enableSwipe(true) // allows to block changing pages using swipe
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .defaultPage(0)
                .enableAnnotationRendering(false) // render annotations (such as comments, colors or forms)
                .password(null)
                .scrollHandle(null)
                .enableAntialiasing(true) // improve rendering a little bit on low-res screens
                // spacing between pages in dp. To define spacing color, set view background
                .spacing(0)
                .load();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_message_context, menu);

        menu.findItem(R.id.menu_message_copy).setVisible(false);
        menu.findItem(R.id.menu_message_resend).setVisible(mShowResend);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_message_forward:
                forwardMediaFile();
                return true;
            case R.id.menu_message_share:
                exportMediaFile();
                return true;
            case R.id.menu_message_resend:
                resendMediaFile();
                return true;


            case R.id.menu_message_delete:
                deleteMediaFile ();

            case R.id.menu_message_nearby:
                sendNearby();
                return true;

            default:
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkPermissions ()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);


        if (permissionCheck ==PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
            return false;
        }

        return true;
    }

    public void sendNearby ()
    {
        if (checkPermissions()) {


        }

    }


    private void deleteMediaFile () {
        Uri deleteUri = mMediaUri;
        if (deleteUri.getScheme() != null && deleteUri.getScheme().equals("vfs"))
        {
            info.guardianproject.iocipher.File fileMedia = new info.guardianproject.iocipher.File(deleteUri.getPath());
            fileMedia.delete();
        }

        Imps.deleteMessageInDb(getContentResolver(), mMessageId);
        setResult(RESULT_OK);
        finish();
    }

    public void exportMediaFile ()
    { if (checkPermissions()) {

            java.io.File exportPath = SecureMediaStore.exportPath(mMimeType, mMediaUri);
            exportMediaFile(mMimeType, mMediaUri, exportPath);

    }
    };

    private void exportMediaFile (String mimeType, Uri mediaUri, java.io.File exportPath)
    {
        try {

            SecureMediaStore.exportContent(mimeType, mediaUri, exportPath);
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(exportPath));
            shareIntent.setType(mimeType);
            startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.export_media)));
        } catch (IOException e) {
            Toast.makeText(this, "Export Failed " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void forwardMediaFile ()
    {

        Intent shareIntent = new Intent(this, ImUrlActivity.class);
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setDataAndType(mMediaUri,mMimeType);
        startActivity(shareIntent);

    }

    private void resendMediaFile ()
    {
        Intent intentResult = new Intent();
        intentResult.putExtra("resendImageUri",mMediaUri);
        intentResult.putExtra("resendImageMimeType",mMimeType);
        setResult(RESULT_OK,intentResult);
        finish();

    }

}
