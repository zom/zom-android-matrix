package info.guardianproject.keanuapp.ui.conversation;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by N-Pex on 2020-05-22.
 */
public class QuickReaction {
    public String reaction;
    public List<String> senders;
    public boolean sentByMe;

    public QuickReaction(String reaction, List<String> senders) {
        this.reaction = reaction;
        if (senders == null) {
            this.senders = new ArrayList<>();
        } else {
            this.senders = senders;
        }
    }
}
