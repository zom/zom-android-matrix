package info.guardianproject.keanuapp.ui.conversation;

import android.content.Intent;
import android.view.View;

import info.guardianproject.keanuapp.R;

import static info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity.REQUEST_ADD_MEDIA;

/**
 * Created by N-Pex on 2019-04-12.
 */
public class StoryViewContrib extends StoryView {

    public StoryViewContrib(StoryActivity activity) {
        super(activity);
        View btnAdd = activity.findViewById(R.id.btnAddNew);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, AddUpdateMediaActivity.class);
                activity.startActivityForResult(intent,REQUEST_ADD_MEDIA);
            }
        });
    }



}
