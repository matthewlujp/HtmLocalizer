package com.example.luning.htmlocalizer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/**
 * Created by luning on 2016/04/04.
 */
public class PageDBHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "pages.db";
    public static final String DB_PAGE_TABLE = "page_contents_table";
    public static final String DB_IMAGE_TABLE = "images_table";
    private static final int DB_VERSION = 1;

    public PageDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table if not exists " +
                DB_PAGE_TABLE + " (" +
                "id integer primary key autoincrement," +
                "title text," +
                "url text not null unique," +
                "domain text not null," +
                "is_main boolean not null," +
                "content text" +
                ")");
        db.execSQL("create table if not exists " +
                DB_IMAGE_TABLE + "(" +
                "id integer primary key autoincrement," +
                "url text not null unique," +
                "domain text not null," +
                "image blob" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
        db.execSQL("drop table if exists " + DB_PAGE_TABLE);
        db.execSQL("drop table if exists " + DB_IMAGE_TABLE);
        onCreate(db);
    }


    // ------------------ For page_contents.table Transaction ------------------
    public static final String PAGE_TRANSACTION_SQL = "INSERT OR REPLACE INTO " + DB_PAGE_TABLE +
            " (title, url, domain, is_main, content) " +
            "VALUES (?,?,?,?,?)";

    public static class PageRecord extends DBTransactionManager.Record{
        public String title, url, domain, content;
        public boolean is_main;
        public PageRecord(String title, String url, String domain,
                          boolean is_main, String content) {
            this.title = title;
            this.url = url;
            this.domain = domain;
            this.is_main = is_main;
            this.content = content;
        }

        @Override
        public SQLiteStatement bindProperties(SQLiteStatement statement) {
            statement.bindString(1, this.title);
            statement.bindString(2, this.url);
            statement.bindString(3, this.domain);
            statement.bindLong(4, this.is_main ? 1 : 0);
            statement.bindString(5, this.content);
            return  statement;
        }
    }

    // ------------------ For images.table Transaction ------------------
    public static final String IMAGE_TRANSACTION_SQL = "INSERT OR REPLACE INTO " + DB_IMAGE_TABLE +
            " (url, domain, image) " +
            "VALUES (?,?,?)";

    public static class ImageRecord extends DBTransactionManager.Record{
        public String url, domain;
        public byte[] img;
        public ImageRecord(String url, String domain, byte[] img) {
            this.url = url;
            this.domain = domain;
            this.img = img;
        }

        @Override
        public SQLiteStatement bindProperties(SQLiteStatement statement) {
            statement.bindString(1, this.url);
            statement.bindString(2, this.domain);
            statement.bindBlob(3, this.img);
            return  statement;
        }
    }
}
