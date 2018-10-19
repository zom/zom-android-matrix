/*
 * Copyright (C) 2007 Esmertec AG. Copyright (C) 2007 The Android Open Source
 * Project
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

package info.guardianproject.keanuapp.model;

import android.support.annotation.NonNull;
import android.text.TextUtils;


import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import info.guardianproject.keanuapp.plugin.xmpp.XmppAddress;
import info.guardianproject.keanuapp.provider.Imps;

/**
 * A ChatSession represents a conversation between two users. A ChatSession has
 * a unique participant which is either another user or a group.
 */
public class ChatSession {

    private ImEntity mParticipant;
    private ChatSessionManager mManager;

    private MessageListener mListener = null;

    private boolean mIsSubscribed = true;

    private boolean mPushSent = false;

    private boolean mCanOmemo = true;

    private Jid mJid = null;
    private XmppAddress mXa = null; //our temporary internal representation

    /**
     * Creates a new ChatSession with a particular participant.
     *
     * @param participant the participant with who the user communicates.
     * @param manager the underlying network connection.
     */
    ChatSession(ImEntity participant, ChatSessionManager manager) {

        if (participant == null)
            throw new RuntimeException ("participant cannot be null");

        mParticipant = participant;
        mManager = manager;

        initJid ();

        //     mHistoryMessages = new Vector<Message>();
    }

    private void initJid ()
    {
        try {
            mJid = JidCreate.from(mParticipant.getAddress().getAddress());

            if (mJid.hasNoResource() && mParticipant instanceof Contact)
            {
                Contact contact = (Contact)mParticipant;
                if (contact.getPresence() != null && contact.getPresence().getResource() != null)
                    mJid = JidCreate.from(mParticipant.getAddress().getBareAddress() + '/' + contact.getPresence().getResource());
            }

            mXa = new XmppAddress(mJid.toString());

            if (mJid.hasNoResource()) {

                if (!TextUtils.isEmpty(mParticipant.getAddress().getResource()))
                {
                    mJid = JidCreate.from(mParticipant.getAddress().getAddress());
                }
                else if (mParticipant instanceof Contact) {
                    String resource = ((Contact) mParticipant).getPresence().getResource();
                    if (!TextUtils.isEmpty(resource)) {
                        mJid = JidCreate.from(mParticipant.getAddress().getBareAddress() + '/' + resource);
                    }
                }

                mXa = new XmppAddress(mJid.toString());
            }

            //not for groups yet
            if (mParticipant instanceof Contact) {
                //if we can't omemo, check it again to be sure
                if (!mCanOmemo) {
                    mCanOmemo = mManager.resourceSupportsOmemo(mJid);
                }
            }

        } catch (XmppStringprepException xe) {
            throw new RuntimeException("Error with address that shouldn't happen: " + xe);
        }
    }

    public ImEntity getParticipant() {
        return mParticipant;
    }

    /**
    public void setParticipant(ImEntity participant) {
        mParticipant = participant;
    }*/

    /**
     * Adds a MessageListener so that it can be notified of any new message in
     * this session.
     *
     * @param listener
     */
    public void setMessageListener(MessageListener listener) {
        mListener = listener;
    }
    
    public MessageListener getMessageListener ()
    {
        return mListener;
    }

    public boolean canOmemo ()
    {
        if (mParticipant instanceof Contact)
            return mCanOmemo;
        else if (mParticipant instanceof ChatGroup)
            return mCanOmemo;
        else
            return false;
    }


    /**
     * Sends a text message to other participant(s) in this session
     * asynchronously and adds the message to the history. TODO: more docs on
     * async callbacks.
     *
     */
    // TODO these sendMessageAsync() should probably be renamed to sendMessageAsyncAndLog()/
    /*
    public void sendMessageAsync(String text) {
        Message message = new Message(text);
        sendMessageAsync(message);
    }*/

    /**
     * Sends a message to other participant(s) in this session asynchronously
     * and adds the message to the history. TODO: more docs on async callbacks.
     *
     * @param message the message to send.
     */
    public int sendMessageAsync(Message message, boolean sendOmemo) {

        if (mParticipant instanceof Contact) {

            if (mJid.hasNoResource())
                initJid();

            message.setTo(mXa);
            message.setType(Imps.MessageType.QUEUED);

            //if we can't omemo, check it again to be sure
            if (!mCanOmemo) {
                mCanOmemo = mManager.resourceSupportsOmemo(mJid);
            }

            mManager.sendMessageAsync(this, message);

        }
        else if (mParticipant instanceof ChatGroup)
        {

            message.setTo(mParticipant.getAddress());
            message.setType(Imps.MessageType.OUTGOING);
            mManager.sendMessageAsync(this, message);

        }
        else
        {
            //what do we do ehre?
            message.setType(Imps.MessageType.QUEUED);
        }

        return message.getType();
    }




    /**
     * Called by ChatSessionManager when received a message of the ChatSession.
     * All the listeners registered in this session will be notified.
     *
     * @param message the received message.
     *
     * @return true if the message was processed correctly, or false
     *   otherwise (e.g. decryption error)
     */
    public boolean onReceiveMessage(Message message, boolean notifyUser) {

        if (mListener != null)
            return mListener.onIncomingMessage(this, message, notifyUser);
        else
            return false;
    }

    public void onMessageReceipt(String id) {
        if (mListener != null)
            mListener.onIncomingReceipt(this, id);

    }

    public void onMessagePostponed(String id) {
        if (mListener != null)
            mListener.onMessagePostponed(this, id);
    }

    public void onReceiptsExpected(boolean isExpected) {
        if (mListener != null)
            mListener.onReceiptsExpected(this, isExpected);
    }

    /**
     * Called by ChatSessionManager when an error occurs to send a message.
     *
     * @param message
     *
     * @param error the error information.
     */
    public void onSendMessageError(Message message, ImErrorInfo error) {
        if (mListener != null)
            mListener.onSendMessageError(this, message, error);

    }

    public void onSendMessageError(String messageId, ImErrorInfo error) {
        /**
        for (Message message : mHistoryMessages) {
            if (messageId.equals(message.getID())) {
                onSendMessageError(message, error);
                return;
            }
        }**/
     //   Log.i("ChatSession", "Message has been removed when we get delivery error:" + error);
    }



    public boolean isSubscribed() {
        return mIsSubscribed;
    }

    public void setSubscribed(boolean isSubscribed) {
        mIsSubscribed = isSubscribed;
    }


    public void updateParticipant (ImEntity participant)
    {
        mParticipant = participant;
    }
}
