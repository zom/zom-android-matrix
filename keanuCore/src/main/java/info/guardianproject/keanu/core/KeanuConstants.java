package info.guardianproject.keanu.core;

public interface KeanuConstants {


    String LOG_TAG = "KeanuApp";

    String EXTRA_INTENT_SEND_TO_USER = "Send2_U";

    String IMPS_CATEGORY = "org.awesomeapp.info.guardianproject.keanuapp.service.IMPS_CATEGORY";
    String ACTION_QUIT = "org.awesomeapp.info.guardianproject.keanuapp.service.QUIT";

    int SMALL_AVATAR_WIDTH = 48;
    int SMALL_AVATAR_HEIGHT = 48;

    int DEFAULT_AVATAR_WIDTH = 196;
    int DEFAULT_AVATAR_HEIGHT = 196;

    String DEFAULT_TIMEOUT_CACHEWORD = "-1"; //one day

    String CACHEWORD_PASSWORD_KEY = "pkey";
    String CLEAR_PASSWORD_KEY = "clear_key";

    String NO_CREATE_KEY = "nocreate";

    String PREFERENCE_KEY_TEMP_PASS = "temppass";

    //ACCOUNT SETTINGS Imps defaults
    String DEFAULT_DEVICE_NAME = "keanu";

    String NOTIFICATION_CHANNEL_ID_SERVICE = "info.guardianproject.keanu.service";
    String NOTIFICATION_CHANNEL_ID_MESSAGE = "info.guardianproject.keanu.message.2";
}
