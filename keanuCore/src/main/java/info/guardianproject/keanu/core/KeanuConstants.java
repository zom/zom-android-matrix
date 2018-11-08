package info.guardianproject.keanu.core;

public interface KeanuConstants {


    public static final String LOG_TAG = "KeanuApp";

    public static final String EXTRA_INTENT_SEND_TO_USER = "Send2_U";

    public static final String IMPS_CATEGORY = "org.awesomeapp.info.guardianproject.keanuapp.service.IMPS_CATEGORY";
    public static final String ACTION_QUIT = "org.awesomeapp.info.guardianproject.keanuapp.service.QUIT";

    public static final int SMALL_AVATAR_WIDTH = 48;
    public static final int SMALL_AVATAR_HEIGHT = 48;

    public static final int DEFAULT_AVATAR_WIDTH = 196;
    public static final int DEFAULT_AVATAR_HEIGHT = 196;

    public static final String DEFAULT_TIMEOUT_CACHEWORD = "-1"; //one day

    public static final String CACHEWORD_PASSWORD_KEY = "pkey";
    public static final String CLEAR_PASSWORD_KEY = "clear_key";

    public static final String NO_CREATE_KEY = "nocreate";

    public static final String PREFERENCE_KEY_TEMP_PASS = "temppass";

    //ACCOUNT SETTINGS Imps defaults
    public static final String DEFAULT_XMPP_RESOURCE = "keanu";
    public static final int DEFAULT_XMPP_PRIORITY = 20;


    public static final String NOTIFICATION_CHANNEL_ID_SERVICE = "info.guardianproject.keanu.service";
    public static final String NOTIFICATION_CHANNEL_ID_MESSAGE = "info.guardianproject.keanu.message.2";
}
