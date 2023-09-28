package ua.com.programmer.agentventa.utility;

public final class Constants {
    private static final String PACKAGE_NAME = "ua.com.programmer.agentventa";

    public static final String ACTION_PROCESS_UPDATES = PACKAGE_NAME+".action.PROCESS_UPDATES";

    /**
     * parameters used to determine which locations is to be saved
     */
    public static final int LOCATION_MIN_DISTANCE = 30;
    public static final int LOCATION_MIN_ACCURACY = 50;

    /**
     * DIRECTIONS API
     */
    //minimal distance between waypoints for directions calculation
    public static final int WAYPOINTS_MIN_DISTANCE = 100;
    //number of points to be sent to directions API in a single request
    public static final int WAYPOINTS_QUANTITY = 20;
    public static final int DIRECTIONS_REQUESTS_DAILY_LIMIT = 20;

    /**
     * document types
     **/
    public static final String DOCUMENT_ORDER = "order";
    public static final String DOCUMENT_CASH = "cash";
    public static final String DOCUMENT_TASK = "task";

    /**
     * measure units
     */
    public static final String UNIT_DEFAULT = "default_unit";
    public static final String UNIT_PACKAGE = "unit_package";
    public static final String UNIT_WEIGHT = "unit_weight";

    /**
     * sync formats
     */
    public static final String SYNC_FORMAT_FTP = "FTP_server";
    public static final String SYNC_FORMAT_WEB = "Web_service";
    public static final String SYNC_FORMAT_HTTP = "HTTP_service";

    /**
     * data update modes
     */
    public static final String UPDATE_ALL = "UPDATE_ALL";
    public static final String UPDATE_SEND_DOCUMENTS = "UPDATE_SEND_DOCUMENTS";
    public static final String UPDATE_CONFIRM = "UPDATE_CONFIRM";
    public static final String UPDATE_PRINT = "UPDATE_PRINT";

    /**
     * error codes for network and data processing issues
     */
    public static final String CONNECTION_ERROR = "CONNECTION_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ERROR_SYNC_IS_ACTIVE = "sync_is_in_active_state";
    public static final String ERROR_DATA_READING = "data_reading_error";

    /**
     * data types
     */
    public static final String DATA_OPTIONS = "options";
    public static final String DATA_CLIENT = "client";
    public static final String DATA_GOODS_ITEM = "item";
    public static final String DATA_PRICE = "price";
    public static final String DATA_COMPETITOR_PRICE = "competitor_price";
    public static final String DATA_DEBT = "debt";
    public static final String DATA_DEBT_DOCUMENT = "debt_document";
    public static final String DATA_IMAGE = "image";
    public static final String DATA_REQUEST_FINISHED = "request_finished";
    public static final String DATA_REQUEST_RESULT = "request_result";
    public static final String DATA_ERROR_CODE = "error_code";
    public static final String DATA_DOCUMENT_SENDING_RESULT = "document_sent";
    public static final String DATA_PUSH_TOKEN = "pushToken";
    public static final String DATA_CLIENT_LOCATION = "client_location";
    public static final String DATA_CLIENT_DIRECTION = "client_direction";
    public static final String DATA_CLIENT_GOODS = "client_goods_item";
    public static final String DATA_LOCATION = "location";
    public static final String DATA_TIME_STAMP = "time_stamp";
    public static final String DATA_PRINT = "print_file";
    public static final String DATA_CLIENT_IMAGE = "client_image";
    public static final String DATA_PAYMENT_TYPE = "payment_type";

    /**
     * options names
     */
    public static final String OPT_COMPETITOR_PRICE = "showCompetitorPrices";
    public static final String OPT_USE_REST_TYPE = "useRestTypeFilter";
    public static final String OPT_FISCAL_PAYMENTS = "fiscalPayments";

    /**
     * fiscal providers
     */
    public static final String FISCAL_PROVIDER_CHECKBOX = "Checkbox";
}
