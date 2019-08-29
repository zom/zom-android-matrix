package info.guardianproject.keanuapp.ui.stories;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.jcodec.containers.mp4.boxes.Edit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.UUID;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IConnectionListener;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.util.SecureMediaStore;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.conversation.AddUpdateMediaActivity;
import jp.wasabeef.richeditor.RichEditor;

import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;
import static info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity.REQUEST_ADD_MEDIA;

public class StoryEditorActivity extends AppCompatActivity {

    private RichEditor mEditor;
    private EditText mEditTitle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_editor);

        setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mEditTitle = findViewById(R.id.editTitle);

        mEditor = (RichEditor) findViewById(R.id.editor);
        mEditor.setEditorFontSize(22);
        mEditor.setPadding(10, 10, 10, 10);
        mEditor.setPlaceholder("");


        mEditor.setOnTextChangeListener(new RichEditor.OnTextChangeListener() {
            @Override public void onTextChange(String text) {
//                mCurrentText = text;
            }
        });

        findViewById(R.id.action_undo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.undo();
            }
        });

        findViewById(R.id.action_redo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.redo();
            }
        });

        findViewById(R.id.action_bold).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setBold();
            }
        });

        findViewById(R.id.action_italic).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setItalic();
            }
        });

        /**
        findViewById(R.id.action_subscript).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setSubscript();
            }
        });

        findViewById(R.id.action_superscript).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setSuperscript();
            }
        });

        findViewById(R.id.action_strikethrough).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setStrikeThrough();
            }
        });

        findViewById(R.id.action_underline).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setUnderline();
            }
        });
            **/

        findViewById(R.id.action_heading1).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(1);
            }
        });

        findViewById(R.id.action_heading2).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(2);
            }
        });

        /**
        findViewById(R.id.action_heading3).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(3);
            }
        });

        findViewById(R.id.action_heading4).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(4);
            }
        });

        findViewById(R.id.action_heading5).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(5);
            }
        });

        findViewById(R.id.action_heading6).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setHeading(6);
            }
        });**/

        /**
        findViewById(R.id.action_txt_color).setOnClickListener(new View.OnClickListener() {
            private boolean isChanged;

            @Override public void onClick(View v) {
                mEditor.setTextColor(isChanged ? Color.BLACK : Color.RED);
                isChanged = !isChanged;
            }
        });

        findViewById(R.id.action_bg_color).setOnClickListener(new View.OnClickListener() {
            private boolean isChanged;

            @Override public void onClick(View v) {
                mEditor.setTextBackgroundColor(isChanged ? Color.TRANSPARENT : Color.YELLOW);
                isChanged = !isChanged;
            }
        });**/

        findViewById(R.id.action_indent).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setIndent();
            }
        });

        findViewById(R.id.action_outdent).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setOutdent();
            }
        });

        findViewById(R.id.action_align_left).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignLeft();
            }
        });

        findViewById(R.id.action_align_center).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignCenter();
            }
        });

        findViewById(R.id.action_align_right).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setAlignRight();
            }
        });

        findViewById(R.id.action_blockquote).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setBlockquote();
            }
        });

        findViewById(R.id.action_insert_bullets).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setBullets();
            }
        });

        findViewById(R.id.action_insert_numbers).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.setNumbers();
            }
        });

        findViewById(R.id.action_add_media).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StoryEditorActivity.this, AddUpdateMediaActivity.class);
                startActivityForResult(intent,REQUEST_ADD_MEDIA);
            }
        });

        findViewById(R.id.action_insert_audio).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {

                final EditText input = new EditText(StoryEditorActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                input.setLayoutParams(lp);
                new AlertDialog.Builder(StoryEditorActivity.this)
                        .setTitle("Add Audio Link (https://somewhere.org/foo.mp3)")
                        .setView(input)

                        // Specifying a listener allows you to take an action before dismissing the dialog.
                        // The dialog is automatically dismissed when a dialog button is clicked.
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with delete operation
                                insertAudio(input.getText().toString());
                            }
                        })

                        // A null listener allows the button to dismiss the dialog and take no further action.
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(R.drawable.ic_audio_24dp)
                        .show();
            }
        });

        findViewById(R.id.action_insert_image).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {

                final EditText input = new EditText(StoryEditorActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                input.setLayoutParams(lp);
                new AlertDialog.Builder(StoryEditorActivity.this)
                        .setTitle("Add Image (https://)")
                        .setView(input)

                        // Specifying a listener allows you to take an action before dismissing the dialog.
                        // The dialog is automatically dismissed when a dialog button is clicked.
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with delete operation

                                String url = input.getText().toString();

                                if (url.endsWith("mp4"))
                                    insertVideo(input.getText().toString());
                                else
                                    insertImage(input.getText().toString(), "image");
                            }
                        })

                        // A null listener allows the button to dismiss the dialog and take no further action.
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(R.drawable.insert_image)
                        .show();
            }
        });

        findViewById(R.id.action_insert_link).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
           //
                final EditText input = new EditText(StoryEditorActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                input.setLayoutParams(lp);
                new AlertDialog.Builder(StoryEditorActivity.this)
                        .setTitle("Add Link (https://)")
                        .setView(input)

                        // Specifying a listener allows you to take an action before dismissing the dialog.
                        // The dialog is automatically dismissed when a dialog button is clicked.
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with delete operation
                                String link = input.getText().toString();
                                mEditor.insertLink(link, link);
                            }
                        })

                        // A null listener allows the button to dismiss the dialog and take no further action.
                        .setNegativeButton(android.R.string.no, null)
                        .setIcon(R.drawable.insert_link
                        )
                        .show();
            }
        });
        /**
        findViewById(R.id.action_insert_checkbox).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mEditor.insertTodo();
            }
        });**/
    }

    //

    private void insertImage (String linkImage, String alt) {

        /**
         String jsInsert = "(function() {" +
         "var audioNode = document.createElement('audio');" +
         "audioNode.setAttribute('controls','');" +
         "var audioSourceNode = document.createElement('source');" +
         "audioNode.setAttribute('src', '" + linkAudio + "');" +
         "audioNode.setAttribute('type', '" + linkType + "');" +
         "audioNode.appendChild(audioSourceNode);" +
         "document.body.appendChild(audioNode);" +
         "}) ();";
         **/

        String html = ("<img src=\"" + linkImage + "\" alt=\"" + alt + "\" style=\"max-width: 100%; width:auto; height: auto;\"/>");
        String jsInsert = "(function (){ var html='" + html + "'; RE.insertHTML(html);})();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mEditor.evaluateJavascript("javascript:" + jsInsert + "", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    Log.d(getClass().getName(),"editor callback: " + value);
                }
            });

        } else {
            mEditor.loadUrl("javascript:" + jsInsert + "");
        }



    }

    private void insertVideo (String linkAudio) {

        /**
         String jsInsert = "(function() {" +
         "var audioNode = document.createElement('audio');" +
         "audioNode.setAttribute('controls','');" +
         "var audioSourceNode = document.createElement('source');" +
         "audioNode.setAttribute('src', '" + linkAudio + "');" +
         "audioNode.setAttribute('type', '" + linkType + "');" +
         "audioNode.appendChild(audioSourceNode);" +
         "document.body.appendChild(audioNode);" +
         "}) ();";
         **/

        String html = ("<video width=\"320\" height=\"240\" controls src=\"" + linkAudio + "\"></video>");
        String jsInsert = "(function (){     document.execCommand('insertHTML', false, '" + html + "');})();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mEditor.evaluateJavascript("javascript:" + jsInsert + "",null);
        } else {
            mEditor.loadUrl("javascript:" + jsInsert + "");
        }



    }

    private void insertAudio (String linkAudio) {

        /**
        String jsInsert = "(function() {" +
                "var audioNode = document.createElement('audio');" +
                "audioNode.setAttribute('controls','');" +
                "var audioSourceNode = document.createElement('source');" +
                "audioNode.setAttribute('src', '" + linkAudio + "');" +
                "audioNode.setAttribute('type', '" + linkType + "');" +
                "audioNode.appendChild(audioSourceNode);" +
                "document.body.appendChild(audioNode);" +
                "}) ();";
         **/

        String html = ("<audio controls src=\"" + linkAudio + "\"></audio>");
        String jsInsert = "(function (){     document.execCommand('insertHTML', false, '" + html + "');})();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mEditor.evaluateJavascript("javascript:" + jsInsert + "",null);
        } else {
            mEditor.loadUrl("javascript:" + jsInsert + "");
        }



    }

    private void appendContent (String newHtml) {

            String jsInsert = "(function() {" +
                    "var divg = document.createElement(\"div\");" +
                    "divg.appendChild(document.createTextNode(\"" + newHtml + "\"));" +
                    "document.body.appendChild(divg);" +
                    "}) ();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mEditor.evaluateJavascript("javascript:" + jsInsert + "",null);
        } else {
            mEditor.loadUrl("javascript:" + jsInsert + "");
        }

    }

    private void saveDraft ()
    {

    }

    private void saveStory ()
    {
        if (mEditor != null) {

            String html = mEditor.getHtml();

            if (!TextUtils.isEmpty(html)) {
                storeHTML(html);
                finish();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_story_editor, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {

            saveDraft();
            finish();
            return true;
        }
        else if (item.getItemId() == R.id.menu_send)
        {
            saveStory();
        }

        return super.onOptionsItemSelected(item);
    }



    private void storeHTML (String html)
    {
        // import
        String sessionId = "self";
        String offerId = UUID.randomUUID().toString();

        try {

            String title = mEditTitle.getText().toString();
            if (TextUtils.isEmpty(title))
                title = "story" + new Date().getTime() + ".html";
            else
                title = URLEncoder.encode(title,"UTF-8")  + ".html";

            final Uri vfsUri = SecureMediaStore.createContentPath(sessionId,title);

            OutputStream out = new FileOutputStream(new File(vfsUri.getPath()));
            String mimeType = "text/html";

            out.write(html.getBytes());
            out.flush();
            out.close();

            //adds in an empty message, so it can exist in the gallery and be forwarded
            Imps.insertMessageInDb(
                    getContentResolver(), false, new Date().getTime(), true, null, vfsUri.toString(),
                    System.currentTimeMillis(), Imps.MessageType.OUTGOING_ENCRYPTED,
                    0, offerId, mimeType, null);


            Intent data = new Intent();
            data.setDataAndType(vfsUri,mimeType);
            setResult(RESULT_OK, data);



        }
        catch (IOException ioe)
        {
            Log.e(LOG_TAG,"error importing photo",ioe);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ADD_MEDIA)
        {
            if (data.hasExtra("resultUris")) {
                String[] mediaUris = data.getStringArrayExtra("resultUris");
                String[] mediaTypes = data.getStringArrayExtra("resultTypes");

                for (int i = 0; i < mediaUris.length; i++) {
                    Uri mediaUri = Uri.parse(mediaUris[i]);
                    try {
                        uploadMediaAsync(mediaUri, mediaUri.getLastPathSegment(), mediaTypes[i]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }

    private void uploadMediaAsync (final Uri mediaPath, final String title, final String type) throws RemoteException, IOException {

        new AsyncTask<Object, Object, Object>()
        {

            @Override
            protected Object doInBackground(Object[] objects) {

                try {
                    uploadMedia (mediaPath, title, type);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
    }

    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            if (msg.what == 1)
            {
                insertMedia(msg.getData().getString("url"),msg.getData().getString("type"));
            }
        }
    };

    private void insertMedia (String url, String type)
    {
        Log.d("upload",url);

        if (!mEditor.hasFocus())
            mEditor.focusEditor();

        if (type.startsWith("image"))
            insertImage(url+"?jpg","image");
        else if (type.startsWith("audio"))
            insertAudio(url);
        else if (type.startsWith("video"))
            insertVideo(url);
        else
            mEditor.insertLink(url, url);

    }

    private void uploadMedia (Uri mediaPath, String title,  final String type) throws RemoteException, IOException {

        ImApp app = (ImApp)getApplication();
        long providerId = app.getDefaultProviderId();
        long accountId = app.getDefaultAccountId();

        String sessionId = "self";

        Uri sendUri = mediaPath;

        if (type.startsWith("image"))
            sendUri = SecureMediaStore.resizeAndImportImage(this, sessionId, mediaPath, type, 480);

        IImConnection conn = RemoteImService.getConnection(providerId, accountId);

        conn.uploadContent(sendUri.toString(), title, type, new IConnectionListener() {
            @Override
            public void onStateChanged(IImConnection connection, int state, ImErrorInfo error) throws RemoteException {

            }

            @Override
            public void onUserPresenceUpdated(IImConnection connection) throws RemoteException {

            }

            @Override
            public void onUpdatePresenceError(IImConnection connection, ImErrorInfo error) throws RemoteException {

            }

            @Override
            public void uploadComplete(String url) throws RemoteException {

                Message msg = new Message();
                msg.what = 1;
                msg.getData().putString("url",url);
                msg.getData().putString("type",type);

                mHandler.sendMessage(msg);
            }

            @Override
            public IBinder asBinder() {
                return null;
            }
        });

    }
}