package info.guardianproject.keanuapp.ui.conversation;

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import info.guardianproject.keanuapp.R;

/**
 * Created by N-Pex on 2019-03-29.
 */
public class StoryActivity extends ConversationDetailActivity {

    // Use this flag as a boolean EXTRA to enable contributor mode (as opposed to viewer mode)
    public static final String ARG_CONTRIBUTOR_MODE = "contributor_mode";

    public static final String TAG_STORYMODE_INDICATOR = "#session";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (contributorMode()) {
            Intent intent = new Intent(this, AddUpdateMediaActivity.class);
            startActivity(intent);
        }
    }

    private boolean contributorMode() {
        return getIntent().getBooleanExtra(ARG_CONTRIBUTOR_MODE, false);
    }

    @Override
    protected int getLayoutFileId() {
        if (contributorMode()) {
            return R.layout.awesome_activity_story_detail_contrib;
        }
        return R.layout.awesome_activity_story_detail;
    }

    @Override
    protected ConversationView createConvoView() {
        if (contributorMode()) {
            return new StoryViewContrib(this);
        }
        return new StoryView(this);
    }

    @Override
    public void applyStyleForToolbar() {
        super.applyStyleForToolbar();
        if (!contributorMode()) {
            getSupportActionBar().setTitle("");
            mToolbar.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
