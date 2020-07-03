package info.guardianproject.keanuapp.nearby;

/**
 * Keanu payload protocol: Invite to room.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Invite {

    public String roomAlias;

    public Invite() {
    }

    public Invite(String roomAlias) {
        this.roomAlias = roomAlias;
    }
}
