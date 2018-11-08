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

package info.guardianproject.keanu.core.service.adapters;


import android.os.Handler;
import android.util.Log;

import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.service.IConnectionListener;
import info.guardianproject.keanu.core.service.IImConnection;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;

public class ConnectionListenerAdapter extends IConnectionListener.Stub {

    private Handler mHandler;

    public ConnectionListenerAdapter(Handler handler) {
        mHandler = handler;
    }

    public void onConnectionStateChange(IImConnection connection, int state, ImErrorInfo error) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnectionStateChange(" + state + ", " + error + ")");
        }
    }

    public void onUpdateSelfPresenceError(IImConnection connection, ImErrorInfo error) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onUpdateSelfPresenceError(" + error + ")");
        }
    }

    public void onSelfPresenceUpdated(IImConnection connection) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onSelfPresenceUpdated()");
        }
    }

    final public void onStateChanged(final IImConnection conn, final int state,
            final ImErrorInfo error) {
        mHandler.post(new Runnable() {
            public void run() {
                onConnectionStateChange(conn, state, error);
            }
        });
    }

    final public void onUpdatePresenceError(final IImConnection conn, final ImErrorInfo error) {
        mHandler.post(new Runnable() {
            public void run() {
                onUpdateSelfPresenceError(conn, error);
            }
        });
    }

    final public void onUserPresenceUpdated(final IImConnection conn) {
        mHandler.post(new Runnable() {
            public void run() {
                onSelfPresenceUpdated(conn);
            }
        });
    }
}
