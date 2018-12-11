/*
 * Copyright (C) 2007-2008 Esmertec AG. Copyright (C) 2007-2008 The Android Open
 * Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.keanu.core.service.adapters;

import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.ChatGroup;
import info.guardianproject.keanu.core.model.ChatGroupManager;
import info.guardianproject.keanu.core.model.ChatSession;
import info.guardianproject.keanu.core.model.ChatSessionListener;
import info.guardianproject.keanu.core.model.ChatSessionManager;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.GroupListener;
import info.guardianproject.keanu.core.model.ImConnection;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.model.impl.BaseAddress;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionListener;
import info.guardianproject.keanu.core.service.IChatSessionManager;


/** manages the chat sessions for a given protocol */
public class ChatSessionManagerAdapter extends IChatSessionManager.Stub {

    ImConnectionAdapter mConnection;
    ChatSessionListenerAdapter mSessionListenerAdapter;
    final RemoteCallbackList<IChatSessionListener> mRemoteListeners = new RemoteCallbackList<IChatSessionListener>();

    HashMap<String, ChatSessionAdapter> mActiveChatSessionAdapters;

    ChatGroupManager groupManager;

    public ChatSessionManagerAdapter(ImConnectionAdapter connection) {

        mConnection = connection;
        ImConnection connAdaptee = connection.getAdaptee();

        connAdaptee.getChatSessionManager().setAdapter(this);

        mActiveChatSessionAdapters = new HashMap<String, ChatSessionAdapter>();
        mSessionListenerAdapter = new ChatSessionListenerAdapter();
        getChatSessionManager().addChatSessionListener(mSessionListenerAdapter);

        groupManager = mConnection.getAdaptee().getChatGroupManager();
        groupManager.addGroupListener(new ChatGroupListenerAdapter());
    }

    public ChatGroupManager getChatGroupManager ()
    {
        return groupManager;
    }
    
    public ChatSessionManager getChatSessionManager() {
        return mConnection.getAdaptee().getChatSessionManager();
    }

    public IChatSession createChatSession(String contactAddress, boolean isNewSession) {

        ContactListManagerAdapter listManager = (ContactListManagerAdapter) mConnection
                .getContactListManager();

        Contact contact = listManager.getContactByAddress(contactAddress);

        if (contact != null) {
            ChatSession session = getChatSessionManager().createChatSession(contact, isNewSession);

            return getChatSessionAdapter(session, isNewSession);
        }
        else
            return null;
    }

    public IChatSession createMultiUserChatSession(String roomAddress, String subject, String nickname, boolean isNewChat)
    {

        try
        {
            ChatGroupManager groupMan = mConnection.getAdaptee().getChatGroupManager();
            groupMan.createChatGroupAsync(roomAddress, subject, nickname);

        }
        catch (Exception e)
        {
            Log.e("Keanu","unable to join group chat" + e.getMessage());

        }

        return null;
    }

    public void closeChatSession(ChatSessionAdapter adapter) {
        synchronized (mActiveChatSessionAdapters) {
            ChatSession session = adapter.getAdaptee();
            getChatSessionManager().closeChatSession(session);

            mActiveChatSessionAdapters.remove(adapter.getAddress());
        }
    }

    public void closeAllChatSessions() {
        synchronized (mActiveChatSessionAdapters) {
            ArrayList<ChatSessionAdapter> adapters = new ArrayList<ChatSessionAdapter>(
                    mActiveChatSessionAdapters.values());
            for (ChatSessionAdapter adapter : adapters) {
                ChatSession session = adapter.getAdaptee();
                getChatSessionManager().closeChatSession(session);

                mActiveChatSessionAdapters.remove(adapter.getAddress());
            }
        }
    }

    public void updateChatSession(String oldAddress, ChatSessionAdapter adapter) {
        synchronized (mActiveChatSessionAdapters) {
            mActiveChatSessionAdapters.remove(oldAddress);
            mActiveChatSessionAdapters.put(adapter.getAddress(), adapter);
        }
    }

    public IChatSession getChatSession(String address) {
        synchronized (mActiveChatSessionAdapters) {
            return mActiveChatSessionAdapters.get(address);
        }
    }

    public List<IChatSession> getActiveChatSessions() {
        synchronized (mActiveChatSessionAdapters) {
            return new ArrayList<IChatSession>(mActiveChatSessionAdapters.values());
        }
    }

    public int getChatSessionCount() {
        synchronized (mActiveChatSessionAdapters) {
            return mActiveChatSessionAdapters.size();
        }
    }

    public void registerChatSessionListener(IChatSessionListener listener) {
        if (listener != null) {
            mRemoteListeners.register(listener);
        }
    }

    public void unregisterChatSessionListener(IChatSessionListener listener) {
        if (listener != null) {
            mRemoteListeners.unregister(listener);
        }
    }

    public synchronized ChatSessionAdapter getChatSessionAdapter(ChatSession session, boolean isNewSession) {

        Address participantAddress = session.getParticipant().getAddress();
        ChatSessionAdapter adapter = mActiveChatSessionAdapters.get(participantAddress.getAddress());

        if (adapter == null) {
            adapter = new ChatSessionAdapter(session, session.getParticipant(), mConnection, isNewSession);
            mActiveChatSessionAdapters.put(participantAddress.getAddress(), adapter);
        }

        return adapter;
    }

    class ChatSessionListenerAdapter implements ChatSessionListener {

        public void onChatSessionCreated(ChatSession session) {
            final IChatSession sessionAdapter = getChatSessionAdapter(session, false);
            final int N = mRemoteListeners.beginBroadcast();
            if (N > 0) {
                for (int i = 0; i < N; i++) {
                    IChatSessionListener listener = mRemoteListeners.getBroadcastItem(i);
                    try {
                        listener.onChatSessionCreated(sessionAdapter);
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing the
                        // dead listeners.
                    }
                }
                mRemoteListeners.finishBroadcast();
            }
        }

        public void notifyChatSessionCreateFailed(final String name, final ImErrorInfo error) {
            final int N = mRemoteListeners.beginBroadcast();
            if (N > 0) {
                for (int i = 0; i < N; i++) {
                    IChatSessionListener listener = mRemoteListeners.getBroadcastItem(i);
                    try {
                        listener.onChatSessionCreateError(name, error);
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing the
                        // dead listeners.
                    }
                }
                mRemoteListeners.finishBroadcast();
            }
        }
    }

    class ChatGroupListenerAdapter implements GroupListener {
        public void onGroupCreated(ChatGroup group) {
        }

        public void onGroupDeleted(ChatGroup group) {
            closeSession(group);
        }

        public void onGroupError(int errorType, String name, ImErrorInfo error) {
            if (errorType == ERROR_CREATING_GROUP) {
                mSessionListenerAdapter.notifyChatSessionCreateFailed(name, error);
            }
        }

        public void onJoinedGroup(ChatGroup group) {
            getChatSessionManager().createChatSession(group,false);
        }

        public void onLeftGroup(ChatGroup group) {
            closeSession(group);
        }

        private void closeSession(ChatGroup group) {
            String address = group.getAddress().getAddress();
            IChatSession session = getChatSession(address);
            if (session != null) {
                closeChatSession((ChatSessionAdapter) session);
            }
        }
    }
}
