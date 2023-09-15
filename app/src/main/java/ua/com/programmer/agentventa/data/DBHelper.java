package ua.com.programmer.agentventa.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import ua.com.programmer.agentventa.utility.Utils;

class DBHelper extends SQLiteOpenHelper {

    private final Utils utils;

    DBHelper(Context context) {
        super(context, "agent", null, 72);
        utils = new Utils();
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        utils.log("w","database created");

        try {
            sqLiteDatabase.execSQL(
                    "create table user_accounts("
                            + "_id integer primary key autoincrement,"
                            + "guid text,"
                            + "extended_id integer,"
                            + "description text,"
                            + "license text,"
                            + "data_format text,"
                            + "db_server text,"
                            + "db_name text,"
                            + "db_user text,"
                            + "db_password text,"
                            + "options text);"
            );
        }catch (SQLiteException e){
            utils.log("e","table user_accounts: "+e);
        }

        sqLiteDatabase.execSQL(
                "create table goods("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"code1 text,"
                        +"code2 text,"
                        +"guid text,"
                        +"vendor_code text,"
                        +"vendor_status text,"
                        +"status text,"
                        +"barcode text,"
                        +"sorting integer,"
                        +"description text,"
                        +"description_lc text,"
                        +"price real,"
                        +"min_price real,"
                        +"base_price real,"
                        +"quantity real,"
                        +"package_only integer,"
                        +"package_value real,"
                        +"weight real,"
                        +"unit text,"
                        +"rest_type text,"
                        +"indivisible integer,"
                        +"groupName text,"
                        +"group_guid text,"
                        +"is_active integer,"
                        +"is_group integer);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_goods on goods(guid,db_guid)");

        sqLiteDatabase.execSQL(
                "create table goods_price("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"item_guid text,"
                        +"price_type integer,"
                        +"price real);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_goods_price on goods_price(item_guid,price_type,db_guid)");

        sqLiteDatabase.execSQL(
                "create table competitor_price("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"item_guid text,"
                        +"client_guid text,"
                        +"price_guid text,"
                        +"description text,"
                        +"notes text,"
                        +"price real);"
        );

        sqLiteDatabase.execSQL(
                "create table images("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"item_guid text,"
                        +"image_guid text,"
                        +"url text,"
                        +"time integer,"
                        +"description text,"
                        +"type text,"
                        +"is_default integer);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_images on images(item_guid,is_default,db_guid)");

        sqLiteDatabase.execSQL(
                "create table price_types("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"code integer,"
                        +"description text);"
        );

        sqLiteDatabase.execSQL(
                "create table clients("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"code1 text,"
                        +"code2 text,"
                        +"guid text,"
                        +"description text,"
                        +"description_lc text,"
                        +"notes text,"
                        +"phone text,"
                        +"address text,"
                        +"discount real,"
                        +"bonus real,"
                        +"price_type integer,"
                        +"is_banned integer,"
                        +"ban_message text,"
                        +"groupName text,"
                        +"group_guid text,"
                        +"is_group integer);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_clients on clients(guid,db_guid)");

        sqLiteDatabase.execSQL(
                "create table orders("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"date text,"
                        +"time integer,"
                        +"time_saved integer,"
                        +"number integer,"
                        +"guid text,"
                        +"delivery_date text,"
                        +"notes text,"
                        +"client_id integer,"
                        +"client_guid text,"
                        +"client_code2 text,"
                        +"client_description text,"
                        +"discount real,"
                        +"price_type integer,"
                        +"payment_type text,"
                        +"status text,"
                        +"price real,"
                        +"quantity real,"
                        +"weight real,"
                        +"discount_value real,"
                        +"next_payment real,"
                        +"latitude real,"
                        +"longitude real,"
                        +"distance real,"
                        +"location_time integer,"
                        +"rest_type text,"
                        +"is_return integer,"
                        +"is_processed integer,"
                        +"is_sent integer);"
        );

        sqLiteDatabase.execSQL(
                "create table orders_content("
                        +"_id integer primary key autoincrement,"
                        +"order_id integer,"
                        +"item_id integer,"
                        +"item_guid text,"
                        +"item_description text,"
                        +"unit_code text,"
                        +"quantity real,"
                        +"weight real,"
                        +"price real,"
                        +"sum real,"
                        +"sum_discount real,"
                        +"is_demand integer,"
                        +"is_packed integer);"
        );

        sqLiteDatabase.execSQL(
                "create table debts("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"client_guid text,"
                        +"doc_id text,"
                        +"doc_guid text,"
                        +"doc_type text,"
                        +"sorting integer,"
                        +"has_content integer,"
                        +"content text,"
                        +"sum real,"
                        +"sum_in real,"
                        +"sum_out real);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_debts on debts(client_guid,doc_id,db_guid)");

        sqLiteDatabase.execSQL(
                "create table info("
                        +"_id integer primary key autoincrement,"
                        +"owner text,"
                        +"owner_guid text,"
                        +"info_field text,"
                        +"info_value text);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_info on info(owner,owner_guid)");

        sqLiteDatabase.execSQL(
                "create table documents("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"date text,"
                        +"time integer,"
                        +"number integer,"
                        +"type text,"
                        +"guid text,"
                        +"notes text,"
                        +"client_guid text,"
                        +"client_description text,"
                        +"status text,"
                        +"sum real,"
                        +"fiscal_number text,"
                        +"is_fiscal integer,"
                        +"is_processed integer,"
                        +"is_sent integer);"
        );

        sqLiteDatabase.execSQL(
                "create table document_content("
                        +"_id integer primary key autoincrement,"
                        +"doc_guid text,"
                        +"item_guid text,"
                        +"item_description text,"
                        +"quantity real,"
                        +"price real,"
                        +"sum real,"
                        +"sum_discount real,"
                        +"is_packed integer);"
        );

        sqLiteDatabase.execSQL(
                "create table document_attributes("
                        +"_id integer primary key autoincrement,"
                        +"doc_guid text,"
                        +"attr_guid text,"
                        +"attr_value text);"
        );

        sqLiteDatabase.execSQL(
                "create table attributes("
                        +"_id integer primary key autoincrement,"
                        +"attr_guid text,"
                        +"attr_name text);"
        );

        sqLiteDatabase.execSQL(
                "create table locations("
                        +"_id integer primary key autoincrement,"
                        +"time integer,"
                        +"accuracy real,"
                        +"altitude real,"
                        +"bearing real,"
                        +"latitude real,"
                        +"longitude real,"
                        +"provider text,"
                        +"speed real,"
                        +"distance real,"
                        +"point_name text,"
                        +"extra text);"
        );

        sqLiteDatabase.execSQL(
                "create table clients_locations("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"client_guid text,"
                        +"latitude real,"
                        +"longitude real,"
                        +"modified integer);"
        );

        sqLiteDatabase.execSQL(
                "create table clients_directions("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"client_guid text,"
                        +"direction_guid text,"
                        +"description text,"
                        +"group_guid text,"
                        +"is_group integer,"
                        +"area text,"
                        +"city text,"
                        +"city_type text,"
                        +"notes text,"
                        +"info text);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_clients_directions on clients_directions(db_guid,client_guid,direction_guid)");

        sqLiteDatabase.execSQL(
                "create table clients_goods("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"time_stamp integer,"
                        +"client_guid text,"
                        +"item_guid text,"
                        +"item_group_guid text,"
                        +"category text,"
                        +"brand text,"
                        +"sort_order integer,"
                        +"no_shipment integer,"
                        +"shipment_date text,"
                        +"shipment_quantity real);"
        );
        sqLiteDatabase.execSQL(
                "create unique index i_clients_goods on clients_goods(db_guid,client_guid,item_guid)");

        sqLiteDatabase.execSQL(
                "create table tasks("
                        +"_id integer primary key autoincrement,"
                        +"db_guid text,"
                        +"guid text,"
                        +"is_done integer,"
                        +"color text,"
                        +"time integer,"
                        +"date text,"
                        +"client_guid text,"
                        +"client_description text,"
                        +"description text,"
                        +"notes text);"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {

        utils.log("w","db upgrade "+oldVersion+" --> "+newVersion);

        if (oldVersion<=34){
            sqLiteDatabase.execSQL("alter table goods add column db_guid text");
            sqLiteDatabase.execSQL("alter table goods_price add column db_guid text");
            sqLiteDatabase.execSQL("alter table price_types add column db_guid text");
            sqLiteDatabase.execSQL("alter table clients add column db_guid text");
            sqLiteDatabase.execSQL("alter table orders add column db_guid text");
            sqLiteDatabase.execSQL("alter table debts add column db_guid text");
            sqLiteDatabase.execSQL("alter table documents add column db_guid text");
        }
        if (oldVersion<=35){
            try {
                sqLiteDatabase.execSQL("drop index i_goods");
            }catch (Exception e){
                utils.log("e","drop index i_goods: "+e);
            }
            try {
                sqLiteDatabase.execSQL("drop index i_goods_price");
            }catch (Exception e){
                utils.log("e","drop index i_goods_price: "+e);
            }
            try {
                sqLiteDatabase.execSQL("drop index i_clients");
            }catch (Exception e){
                utils.log("e","drop index i_clients: "+e);
            }
            try {
                sqLiteDatabase.execSQL("drop index i_debts");
            }catch (Exception e){
                utils.log("e","drop index i_debts: "+e);
            }
            sqLiteDatabase.execSQL("create unique index i_goods on goods(guid,db_guid)");
            sqLiteDatabase.execSQL("create unique index i_goods_price on goods_price(item_guid,price_type,db_guid)");
            sqLiteDatabase.execSQL("create unique index i_clients on clients(guid,db_guid)");
            sqLiteDatabase.execSQL("create unique index i_debts on debts(client_guid,doc_id,db_guid)");
        }
        if (oldVersion<=36){
            sqLiteDatabase.execSQL("drop table if exists clients_locations");
            sqLiteDatabase.execSQL(
                    "create table clients_locations("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"client_guid text,"
                            +"latitude real,"
                            +"longitude real);"
            );
        }
        if (oldVersion<=37){
            sqLiteDatabase.execSQL("alter table clients_locations add column modified integer");
        }
        if (oldVersion<=38){
            try {
                sqLiteDatabase.execSQL("alter table user_accounts add column extended_id integer");
            }catch (SQLiteException e){
                utils.log("e","user_accounts add column extended_id: "+e);
            }
        }
        if (oldVersion<=39){
            try {
                sqLiteDatabase.execSQL("alter table user_accounts add column license text");
            }catch (SQLiteException e){
                utils.log("e","user_accounts add column license: "+e);
            }
        }
        if (oldVersion<=40){
            try {
                sqLiteDatabase.execSQL("alter table orders add column latitude real");
                sqLiteDatabase.execSQL("alter table orders add column longitude real");
                sqLiteDatabase.execSQL("alter table orders add column distance real");
            }catch (SQLiteException e){
                utils.log("e","orders add columns lat, lng, dist: "+e);
            }
        }
        if (oldVersion<=41){
            try {
                sqLiteDatabase.execSQL("alter table orders add column weight real");
                sqLiteDatabase.execSQL("alter table orders_content add column weight real");
            }catch (SQLiteException e){
                utils.log("e","orders add column weight: "+e);
            }
        }
        if (oldVersion<=42){
            try {
                sqLiteDatabase.execSQL("alter table goods add column vendor_code text");
            }catch (SQLiteException e){
                utils.log("e","goods add column vendor_code: "+e);
            }
        }
        if (oldVersion<=43){
            sqLiteDatabase.execSQL("drop table if exists images");
            sqLiteDatabase.execSQL(
                    "create table images("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"item_guid text,"
                            +"image_guid text,"
                            +"url text,"
                            +"time integer,"
                            +"description text,"
                            +"type text,"
                            +"is_default integer);"
            );
            sqLiteDatabase.execSQL(
                    "create unique index i_images on images(item_guid,image_guid)");
        }
        if (oldVersion<=44){
            sqLiteDatabase.execSQL("drop index i_images");
            sqLiteDatabase.execSQL(
                    "create unique index i_images on images(item_guid,image_guid,db_guid)");
        }
        if (oldVersion<=45){
            sqLiteDatabase.execSQL("delete from images");
            sqLiteDatabase.execSQL("drop index i_images");
            sqLiteDatabase.execSQL(
                    "create unique index i_images on images(item_guid,is_default,db_guid)");
        }
        if (oldVersion<=46){
            sqLiteDatabase.execSQL("alter table clients add column notes text");
        }
        if (oldVersion<=47){
            sqLiteDatabase.execSQL("drop table if exists debts");
            sqLiteDatabase.execSQL(
                    "create table debts("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"client_guid text,"
                            +"doc_id text,"
                            +"doc_guid text,"
                            +"doc_type text,"
                            +"sorting integer,"
                            +"has_content integer,"
                            +"content text,"
                            +"sum real);"
            );
            sqLiteDatabase.execSQL(
                    "create unique index i_debts on debts(client_guid,doc_id,db_guid)");
        }
        if (oldVersion<=48){
            sqLiteDatabase.execSQL("alter table user_accounts add column options text");
        }
        if (oldVersion<=49){
            sqLiteDatabase.execSQL(
                    "create table clients_directions("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"client_guid text,"
                            +"direction_guid text,"
                            +"description text,"
                            +"is_group integer,"
                            +"area text,"
                            +"city text,"
                            +"city_type text,"
                            +"notes text,"
                            +"info text);"
            );
            sqLiteDatabase.execSQL(
                    "create unique index i_clients_directions on clients_directions(db_guid,client_guid,direction_guid)");
        }
        if (oldVersion<=50){
            sqLiteDatabase.execSQL("alter table goods add column time_stamp integer");
            sqLiteDatabase.execSQL("alter table clients add column time_stamp integer");
        }
        if (oldVersion<=51){
            sqLiteDatabase.execSQL("alter table clients add column bonus real");
        }
        if (oldVersion<=52){
            sqLiteDatabase.execSQL("alter table clients_directions add column group_guid text");
        }
        if (oldVersion<=53){
            sqLiteDatabase.execSQL("alter table clients_directions add column time_stamp integer");
        }
        if (oldVersion<=54){
            sqLiteDatabase.execSQL("alter table goods_price add column time_stamp integer");
            sqLiteDatabase.execSQL("alter table debts add column time_stamp integer");
        }
        if (oldVersion<=55){
            sqLiteDatabase.execSQL("alter table debts add column sum_in real");
            sqLiteDatabase.execSQL("alter table debts add column sum_out real");
        }
        if (oldVersion<=56){
            sqLiteDatabase.execSQL(
                    "create table clients_goods("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"time_stamp integer,"
                            +"client_guid text,"
                            +"item_guid text,"
                            +"sort_order integer,"
                            +"no_shipment integer,"
                            +"shipment_date text,"
                            +"shipment_quantity real);"
            );
            sqLiteDatabase.execSQL(
                    "create unique index i_clients_goods on clients_goods(db_guid,client_guid,item_guid)");
            sqLiteDatabase.execSQL(
                    "create table tasks("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"guid text,"
                            +"is_done integer,"
                            +"color text,"
                            +"time integer,"
                            +"date text,"
                            +"client_guid text,"
                            +"description text,"
                            +"notes text);"
            );
        }
        if (oldVersion<=57){
            sqLiteDatabase.execSQL("alter table orders add column time_saved integer");
            sqLiteDatabase.execSQL("alter table orders add column location_time integer");
        }
        if (oldVersion<=58){
            sqLiteDatabase.execSQL("alter table tasks add column client_description text");
        }
        if (oldVersion<=59){
            sqLiteDatabase.execSQL("alter table clients_goods add column item_group_guid text");
        }
        if (oldVersion<=60){
            sqLiteDatabase.execSQL("alter table goods add column rest_type text");
        }
        if (oldVersion<=61){
            sqLiteDatabase.execSQL("alter table goods add column is_active integer");
        }
        if (oldVersion<=62){
            sqLiteDatabase.execSQL("alter table orders_content add column is_demand integer");
        }
        if (oldVersion<=63){
            sqLiteDatabase.execSQL("alter table clients_goods add column category text");
            sqLiteDatabase.execSQL("alter table clients_goods add column brand text");
        }
        if (oldVersion <= 65) {
            sqLiteDatabase.execSQL("drop index i_clients_goods");
            sqLiteDatabase.execSQL(
                    "create unique index i_clients_goods on clients_goods(db_guid,client_guid,item_guid)");
        }
        if (oldVersion <= 66){
            sqLiteDatabase.execSQL("alter table goods add column vendor_status text");
        }
        if (oldVersion <= 67){
            sqLiteDatabase.execSQL("alter table clients add column is_banned integer");
            sqLiteDatabase.execSQL("alter table clients add column ban_message text");
        }
        if (oldVersion <= 68){
            sqLiteDatabase.execSQL("drop table if exists competitor_price");
            sqLiteDatabase.execSQL(
                    "create table competitor_price("
                            +"_id integer primary key autoincrement,"
                            +"db_guid text,"
                            +"time_stamp integer,"
                            +"item_guid text,"
                            +"client_guid text,"
                            +"price_guid text,"
                            +"description text,"
                            +"notes text,"
                            +"price real);"
            );
        }
        if (oldVersion <= 69){
            sqLiteDatabase.execSQL("alter table orders add column rest_type text");
        }
        if (oldVersion <= 70){
            sqLiteDatabase.execSQL("alter table goods add column status text");
        }
        if (oldVersion <= 71){
            sqLiteDatabase.execSQL("alter table documents add column fiscal_number text");
            sqLiteDatabase.execSQL("alter table documents add column is_fiscal integer");
        }
    }

}
