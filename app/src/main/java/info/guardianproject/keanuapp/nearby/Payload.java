package info.guardianproject.keanuapp.nearby;

/**
 * Keanu payload protocol for AirShare.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Payload {

    public Invite invite;

    public Payload() {
    }

    public Payload(Invite invite) {
        this.invite = invite;
    }
}
