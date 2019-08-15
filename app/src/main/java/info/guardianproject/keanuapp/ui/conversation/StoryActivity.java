package info.guardianproject.keanuapp.ui.conversation;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;

import info.guardianproject.keanu.core.util.SystemServices;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.widgets.ShareRequest;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

/**
 * Created by N-Pex on 2019-03-29.
 */
public class StoryActivity extends ConversationDetailActivity {

    // Use this flag as a boolean EXTRA to enable contributor mode (as opposed to viewer mode)
    public static final String ARG_CONTRIBUTOR_MODE = "contributor_mode";

    private StoryView storyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (contributorMode()) {
            Intent intent = new Intent(this, AddUpdateMediaActivity.class);
            startActivityForResult(intent,REQUEST_ADD_MEDIA);
        }
    }

    private boolean contributorMode() {
        return getIntent().getBooleanExtra(ARG_CONTRIBUTOR_MODE, false);
    }

    @Override
    protected int getLayoutFileId() {
      //  if (contributorMode()) {
        //    return R.layout.awesome_activity_story_detail_contrib;
       // }
        return R.layout.awesome_activity_story_detail;
    }

    @Override
    protected ConversationView createConvoView() {
        if (contributorMode()) {
            return (storyView = new StoryViewContrib(this));
        }
        return (storyView = new StoryView(this));
    }

    @Override
    public void applyStyleForToolbar() {
        super.applyStyleForToolbar();
        if (!contributorMode()) {
            getSupportActionBar().setTitle("");
            mToolbar.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    protected void sendMedia (Uri mediaUri, String mimeType, boolean deleteFile)
    {
        ShareRequest request = new ShareRequest();
        request.deleteFile = false;
        request.resizeImage = true;
        request.importContent = true;
        request.media = mediaUri;
        request.mimeType = mimeType;

        if (TextUtils.isEmpty(request.mimeType)) {
            // import
            SystemServices.FileInfo info = null;
            try {
                info = SystemServices.getFileInfoFromURI(this, request.media);
                request.mimeType = info.type;
                info.stream.close();
            } catch (Exception e) {

            }

        }

        boolean resizeImage = false;
        boolean importContent = true; //let's import it!

        handleSendDelete(request.media, request.mimeType, deleteFile, resizeImage, importContent);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
            super.onActivityResult(requestCode, resultCode, resultIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_conversation_detail_live, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        storyView.pause();
    }
}
