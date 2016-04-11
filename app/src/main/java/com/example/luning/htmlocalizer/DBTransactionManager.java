package com.example.luning.htmlocalizer;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by luning on 2016/04/04.
 *
 * Thread safe.
 */
public class DBTransactionManager<T extends DBTransactionManager.Record> {
    private SQLiteDatabase mDatabase;
    private boolean on_transaction = false;
    private int recordNum = 0;
    private String mSql;
    private Lock initLock = new ReentrantLock(),
        addLock = new ReentrantLock();
    private ArrayList<T> mRecords;

    public static abstract class Record {
        public abstract SQLiteStatement bindProperties(SQLiteStatement statement);
    }

    public DBTransactionManager(SQLiteDatabase db) {
        mDatabase = db;
    }

    public void initializeTransaction(String sql) throws Exception {
        initLock.lock();
        if (on_transaction) {
            throw new Exception("In the middle of a transaction.");
        }

        mSql = sql;
        mRecords = new ArrayList<T>();
        recordNum = 0;
        on_transaction = true;
        initLock.unlock();
    }

    public void addRecord(T record) throws Exception {
        addLock.lock();
        if (!on_transaction) {
            addLock.unlock();
            throw new Exception("Transaction has not initialized.");
        }
        mRecords.add(record);
        recordNum++;
        addLock.unlock();
    }

    public int getRecordNum() { return recordNum; }

    public void commitTransaction(boolean continueTransaction) throws Exception {
        addLock.lock();
        if (!on_transaction) {
            throw new Exception("Transaction has not initialized.");
        }
        mDatabase.beginTransaction();
        SQLiteStatement compiledSql = mDatabase.compileStatement(mSql);

        while (mRecords.size() > 0) {
            T record = mRecords.remove(0);
            compiledSql = record.bindProperties(compiledSql);
            compiledSql.executeInsert();
        }

        try {
            mDatabase.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.e("commitTransaction", e.toString());
        } finally {
            on_transaction = false;
            mDatabase.endTransaction();
        }
        if (continueTransaction) {
            initializeTransaction(mSql);
        }
        addLock.unlock();
    }
}
