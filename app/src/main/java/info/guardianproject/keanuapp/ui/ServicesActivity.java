/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.guardianproject.keanuapp.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;


import java.util.ArrayList;

import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.ui.bots.ServicesRecyclerViewAdapter;
import info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity;
import info.guardianproject.keanuapp.ui.conversation.StoryActivity;

public class ServicesActivity extends BaseActivity implements ServicesRecyclerViewAdapter.ServiceItemCallback {

    private Snackbar mSbStatus;
    private RecyclerView mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.awesome_activity_services);
        setTitle(R.string.action_services);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        applyStyleForToolbar();

        mView = (RecyclerView)findViewById(R.id.recyclerServices);
        mView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mView.setAdapter(new ServicesRecyclerViewAdapter(this, this));
    }


    public void applyStyleForToolbar() {



        //not set color
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        int selColor = settings.getInt("themeColor",-1);

        if (selColor != -1) {
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setNavigationBarColor(selColor);
                getWindow().setStatusBarColor(selColor);
            }

            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(selColor));
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBotClicked(final String jid, final String nickname) {
        ImApp app = (ImApp)getApplication();
        IImConnection conn = RemoteImService.getConnection(app.getDefaultProviderId(),app.getDefaultAccountId());

        ArrayList<String> invites = new ArrayList<>();
        invites.add(jid);
        startGroupChat(nickname,invites,conn,false,true,false);


    }

    private IImConnection mLastConnGroup = null;
    private long mRequestedChatId = -1;

    public void startGroupChat (String roomSubject, final ArrayList<String> invitees, IImConnection conn, boolean isEncrypted, boolean isPrivate, boolean isSession)
    {
        mLastConnGroup = conn;

        try {
            /**
             if (TextUtils.isEmpty(roomSubject))
             {
             roomSubject = getString(R.string.new_group_title);
             }**/

            IChatSessionManager manager = mLastConnGroup.getChatSessionManager();

            String[] aInvitees = null;

            if (invitees != null)
                aInvitees = invitees.toArray(new String[invitees.size()]);

            mSbStatus = Snackbar.make(mView, R.string.connecting_to_group_chat_, Snackbar.LENGTH_INDEFINITE);
            mSbStatus.show();

            manager.createMultiUserChatSession(null, roomSubject, null, true, aInvitees, isEncrypted, isPrivate, new IChatSessionListener() {

                @Override
                public IBinder asBinder() {
                    return null;
                }

                @Override
                public void onChatSessionCreated(final IChatSession session) throws RemoteException {

                    mSbStatus.dismiss();

                    session.setLastMessage(" ");
                    Intent intent = new Intent(ServicesActivity.this,  isSession ? StoryActivity.class : ConversationDetailActivity.class);
                    intent.putExtra("id", session.getId());
                    intent.putExtra("firsttime",true);

                    boolean isEmptyGroup = invitees == null || invitees.size() == 0;
                    intent.putExtra("isNew", isEmptyGroup);
                    intent.putExtra("subject", roomSubject);
                    intent.putExtra("nickname", roomSubject);

                    if (isSession)
                        intent.putExtra(StoryActivity.ARG_CONTRIBUTOR_MODE,true);

                    startActivity(intent);
                }

                @Override
                public void onChatSessionCreateError(String name, ImErrorInfo error) throws RemoteException {

                    mSbStatus.dismiss();

                    String errorMessage = getString(R.string.error);
                    if (error != null)
                        errorMessage = error.getDescription();

                    mSbStatus = Snackbar.make(mView, errorMessage, Snackbar.LENGTH_LONG);
                    mSbStatus.show();
                }
            });


        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

}
