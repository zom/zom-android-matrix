package info.guardianproject.keanuapp.ui.stories;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanuapp.R;

public class StoryGalleryActivity extends AppCompatActivity {
    public static int GALLERY_MODE_ALL = 0;
    public static int GALLERY_MODE_PDF = 1;
    public static int GALLERY_MODE_IMAGE = 2;
    public static int GALLERY_MODE_VIDEO = 3;
    public static int GALLERY_MODE_AUDIO = 4;

    public static String ARG_GALLERY_MODE = "gallery_mode";

    private static final int REQUEST_CODE_READ_PERMISSIONS = 1;

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private RecyclerView recyclerViewGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Secured from screen shots?
        if (Preferences.doBlockScreenshots()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }

        setContentView(R.layout.awesome_activity_story_gallery);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (recyclerViewGallery.getAdapter() != null && recyclerViewGallery.getAdapter() instanceof GalleryAdapter) {
                    GalleryAdapter adapter = (GalleryAdapter)recyclerViewGallery.getAdapter();
                    switch (tab.getPosition()) {
                        case 1:
                            adapter.setMediaType(GalleryAdapter.MediaType.Pdf);
                            break;
                        case 2:
                            adapter.setMediaType(GalleryAdapter.MediaType.Image);
                            break;
                        case 3:
                            adapter.setMediaType(GalleryAdapter.MediaType.Video);
                            break;
                        case 4:
                            adapter.setMediaType(GalleryAdapter.MediaType.Audio);
                            break;
                        default:
                            adapter.setMediaType(GalleryAdapter.MediaType.All);
                            break;
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        recyclerViewGallery = findViewById(R.id.rvGallery);
        int spanCount = 5;
        GridLayoutManager llm = new GridLayoutManager(this, spanCount, GridLayoutManager.VERTICAL, false);
        recyclerViewGallery.setLayoutManager(llm);
        int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.story_contrib_gallery_padding);
        recyclerViewGallery.addItemDecoration(new GalleryItemDecoration(spanCount, spacingInPixels, false));
        setGalleryAdapter();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void setGalleryAdapter() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_READ_PERMISSIONS);
            return;
        }
        recyclerViewGallery.setAdapter(new GalleryAdapter(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_READ_PERMISSIONS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setGalleryAdapter();
            }
        }
    }
}
