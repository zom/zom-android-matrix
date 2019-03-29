package info.guardianproject.keanuapp.ui.conversation;

import android.graphics.Color;

import info.guardianproject.keanuapp.R;

/**
 * Created by N-Pex on 2019-03-29.
 */
public class StoryActivity extends ConversationDetailActivity {
    @Override
    protected int getLayoutFileId() {
        return R.layout.awesome_activity_story_detail;
    }

    @Override
    protected ConversationView createConvoView() {
        return new StoryView(this);
    }

    @Override
    public void applyStyleForToolbar() {
        super.applyStyleForToolbar();
        getSupportActionBar().setTitle("");
        mToolbar.setBackgroundColor(Color.TRANSPARENT);
    }
}
