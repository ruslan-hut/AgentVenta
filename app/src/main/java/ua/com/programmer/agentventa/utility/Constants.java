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
    public static final String SYNC_FORMAT_WEBSOCKET = "WebSocket_relay";

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
    public static final String DATA_COMPANY = "company";
    public static final String DATA_STORE = "store";
    public static final String DATA_REST = "rest";
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

    /**
     * WebSocket configuration
     */
    public static final int WEBSOCKET_RECONNECT_INITIAL_DELAY = 1000; // 1s
    public static final int WEBSOCKET_RECONNECT_MAX_DELAY = 60000; // 60s
    public static final int WEBSOCKET_PING_INTERVAL = 30000; // 30s

    // Idle interval for periodic connection checks (when no pending data)
    public static final long WEBSOCKET_IDLE_INTERVAL_DEFAULT = 15 * 60 * 1000L; // 15 min
    public static final long WEBSOCKET_IDLE_INTERVAL_MIN = 5 * 60 * 1000L; // 5 min
    public static final long WEBSOCKET_IDLE_INTERVAL_MAX = 60 * 60 * 1000L; // 60 min

    /**
     * WebSocket message types
     */
    public static final String WEBSOCKET_MESSAGE_TYPE_DATA = "data";
    public static final String WEBSOCKET_MESSAGE_TYPE_ACK = "ack";
    public static final String WEBSOCKET_MESSAGE_TYPE_PING = "ping";
    public static final String WEBSOCKET_MESSAGE_TYPE_PONG = "pong";
    public static final String WEBSOCKET_MESSAGE_TYPE_ERROR = "error";
    public static final String WEBSOCKET_MESSAGE_TYPE_SYNC_SETTINGS = "sync_settings";

    // Document sync message types
    public static final String WEBSOCKET_MESSAGE_TYPE_UPLOAD_ORDER = "upload_order";
    public static final String WEBSOCKET_MESSAGE_TYPE_UPLOAD_CASH = "upload_cash";
    public static final String WEBSOCKET_MESSAGE_TYPE_UPLOAD_IMAGE = "upload_image";
    public static final String WEBSOCKET_MESSAGE_TYPE_UPLOAD_LOCATION = "upload_location";
    public static final String WEBSOCKET_MESSAGE_TYPE_DOWNLOAD_CATALOGS = "download_catalogs";
    public static final String WEBSOCKET_MESSAGE_TYPE_SYNC_COMPLETE = "sync_complete";

    /**
     * WebSocket data types (for payload.data_type field)
     */
    public static final String WEBSOCKET_DATA_TYPE_SETTINGS = "settings";
    public static final String WEBSOCKET_DATA_TYPE_OPTIONS = "options";
    public static final String WEBSOCKET_DATA_TYPE_ORDER = "order";
    public static final String WEBSOCKET_DATA_TYPE_CASH = "cash";
    public static final String WEBSOCKET_DATA_TYPE_IMAGE = "image";
    public static final String WEBSOCKET_DATA_TYPE_LOCATION = "location";
    public static final String WEBSOCKET_DATA_TYPE_CATALOG = "catalog";

    /**
     * Value IDs for data objects (value_id field in payload items)
     * Used to identify object type in unified array payloads
     */
    public static final String VALUE_ID_OPTIONS = "options";
    public static final String VALUE_ID_CLIENTS = "clients";
    public static final String VALUE_ID_GOODS = "goods";
    public static final String VALUE_ID_DEBTS = "debts";
    public static final String VALUE_ID_PAYMENT_TYPES = "payment_types";
    public static final String VALUE_ID_COMPANIES = "companies";
    public static final String VALUE_ID_STORES = "stores";
    public static final String VALUE_ID_RESTS = "rests";
    public static final String VALUE_ID_CLIENTS_LOCATIONS = "clients_locations";
    public static final String VALUE_ID_CLIENTS_DIRECTIONS = "clients_directions";
    public static final String VALUE_ID_CLIENTS_GOODS = "clients_goods";
    public static final String VALUE_ID_IMAGES = "images";

    /**
     * Device status values from server
     */
    public static final String DEVICE_STATUS_PENDING = "pending";
    public static final String DEVICE_STATUS_APPROVED = "approved";
    public static final String DEVICE_STATUS_DENIED = "denied";
}
