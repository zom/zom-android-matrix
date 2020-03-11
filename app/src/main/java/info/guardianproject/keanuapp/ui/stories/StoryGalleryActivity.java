package info.guardianproject.keanuapp.ui.stories;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import info.guardianproject.keanu.core.Preferences;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.widgets.MediaInfo;

public class StoryGalleryActivity extends AppCompatActivity implements GalleryAdapter.GalleryAdapterListener {
    public static int GALLERY_MODE_ALL = 0;
    public static int GALLERY_MODE_PDF = 1;
    public static int GALLERY_MODE_IMAGE = 2;
    public static int GALLERY_MODE_VIDEO = 3;
    public static int GALLERY_MODE_AUDIO = 4;

    public static String ARG_GALLERY_MODE = "gallery_mode";
    public static String RESULT_SELECTED_MEDIA = "selected_media";

    private static final int REQUEST_CODE_READ_PERMISSIONS = 1;

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;

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

        viewPager = findViewById(R.id.viewPager);
        viewPager.setOffscreenPageLimit(5);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int i) {
                tabLayout.getTabAt(i).select();
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        setGalleryAdapter();

        // Check for args to select one of the tabs
        int galleryMode = getIntent().getIntExtra(ARG_GALLERY_MODE, GALLERY_MODE_ALL);
        tabLayout.getTabAt(galleryMode).select();
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
        viewPager.setAdapter(new GalleryPagerAdapter());
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

    @Override
    public void onMediaItemClicked(MediaInfo media) {
        Intent resultData = new Intent();
        resultData.putExtra(RESULT_SELECTED_MEDIA, media);
        setResult(RESULT_OK, resultData);
        finish();
    }

    private class GalleryPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 5;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return (View)o == view;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Context context = StoryGalleryActivity.this;
            RecyclerView rv = new RecyclerView(context);
            int spanCount = 5;

            GalleryAdapter adapter = new GalleryAdapter(context, StoryGalleryActivity.this);
            RecyclerView.LayoutManager llm = new GridLayoutManager(context, spanCount, GridLayoutManager.VERTICAL, false);

            switch (position) {
                case 1:
                    adapter.setMediaType(GalleryAdapter.MediaType.Pdf);
                    llm = new LinearLayoutManager(context);
                    break;
                case 2:
                    adapter.setMediaType(GalleryAdapter.MediaType.Image);
                    break;
                case 3:
                    adapter.setMediaType(GalleryAdapter.MediaType.Video);
                    break;
                case 4:
                    adapter.setMediaType(GalleryAdapter.MediaType.Audio);
                    llm = new LinearLayoutManager(context);
                    break;
                default:
                    adapter.setMediaType(GalleryAdapter.MediaType.All);
                    break;
            }

            rv.setLayoutManager(llm);
            int spacingInPixels = getResources().getDimensionPixelSize(R.dimen.story_contrib_gallery_padding);
            rv.addItemDecoration(new GalleryItemDecoration(spanCount, spacingInPixels, false));
            rv.setAdapter(adapter);

            container.addView(rv);
            return rv;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View)object);
        }
    }
}
