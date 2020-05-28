package info.guardianproject.keanuapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import info.guardianproject.keanuapp.ui.stories.MultiSelectionGalleryAdapter;
import info.guardianproject.keanuapp.ui.stories.StoryEditorActivity;

import static info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity.REQUEST_ADD_MEDIA;

public class MultipleImageSelectionActivity extends AppCompatActivity {

    Toolbar toolbar;
    private RecyclerView list_image;
    private ImageView btn_send;
    private String storyTitle;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiple_selection);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        btn_send = (ImageView)findViewById(R.id.btn_send);
        setSupportActionBar(toolbar);
        setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        list_image = (RecyclerView)findViewById(R.id.list_image);
        list_image.setLayoutManager(new GridLayoutManager(this, 3));

        MultiSelectionGalleryAdapter multiSelectionGalleryAdapter = new MultiSelectionGalleryAdapter(getApplicationContext());
        list_image.setAdapter(multiSelectionGalleryAdapter);
        Intent intent = getIntent();
        storyTitle = intent.getStringExtra("title");
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(multiSelectionGalleryAdapter != null){

                    Intent result = new Intent();
                    result.putExtra("listMediaInfo",multiSelectionGalleryAdapter.getCheckedList());
                    result.putExtra("title",storyTitle);
                    setResult(Activity.RESULT_OK, result);
                    finish();
                }
            }
        });
    }

    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
