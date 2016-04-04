package com.example.luning.htmlocalizer;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by luning on 2016/04/03.
 */
public class CrawlService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private static final int JOB_MAX_LIMIT = 80;
    private static final int TASK_WAITING_AMOUNT_LIMIT = 50;
    private static final int CRAWLED_AMOUNT_LIMIT = 800;
    private static final String DB_NAME = "PAGES_DB";
    private static final String DB_TABLE = "PAGES_CONTENT_TABLE";
    private static final int DB_VERSION = 1;
    private SQLiteDatabase mDB;
    private Page startPage;
    private boolean stop_flag = true;
    private ArrayList<Crawler> mTaskQueue = new ArrayList<Crawler>();
    private ArrayBlockingQueue<Crawler> mOnRunQueue = new ArrayBlockingQueue<Crawler>(JOB_MAX_LIMIT);
    private ArrayList<URL> mVisitedLinks;
    private final Lock lock = new ReentrantLock();
    private final Condition taskNotFull = lock.newCondition(),
        taskNotEmpty = lock.newCondition(),
        taskNotTooMany = lock.newCondition();

    public class LocalBinder extends Binder {
        CrawlService getService() {
            return CrawlService.this;
        }
    }


    @Override
    public void onCreate() {
        // Get database object
        DBHelper dbHelper = new DBHelper(this);
        mDB = dbHelper.getWritableDatabase();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {

    }

    // Data Base Helper
    private static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("create table if not exists " +
                    DB_TABLE + "(" +
                    "id integer primary key autoincrement," +
                    "title text," +
                    "url text not null unique," +
                    "domain text not null," +
                    "is_main boolean not null," +
                    "content text" +
                    ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
            db.execSQL("drop table if exists " + DB_TABLE);
            onCreate(db);
        }
    }

    private class Crawler extends AsyncTask<Void, Integer, String> {
        private URL mUrl;
        private ArrayList<URL> newLinks = null;

        public Crawler(URL url) {
            mUrl = url;
        }

        @Override
        protected String doInBackground(Void... params) {
            Log.e("doInBackgroud", "1");
            Page page = new Page(mUrl);
            // Crawled too many or already visited
            if (mVisitedLinks.size() > CRAWLED_AMOUNT_LIMIT || mVisitedLinks.contains(page.getPageUrl())) {
                return "";
            }
            Log.e("visitedLinks", String.valueOf(mVisitedLinks.size()));
            HttpURLConnection con = null;
            BufferedReader br = null;
            StringBuilder response = new StringBuilder();
            Log.e("doInBackgroud", "2");
            try {
                con = (HttpURLConnection) mUrl.openConnection();
                con.setRequestMethod("GET");
                con.setInstanceFollowRedirects(false);
                con.setDoInput(true);
                con.setDoOutput(false);
                Log.e("doInBackgroud", "3");
                con.connect();

                Log.e("doInBackgroud", "4");
                br = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String str;
                Log.e("doInBackgroud", "5");
                while ((str = br.readLine()) != null) {
                    response.append(str);
                }
                Log.e("doInBackgroud", "6");

            } catch (Exception e) {
                Log.e("Crawler", e.toString());
            } finally {
                try {
                    if (con != null) {
                        con.disconnect();
                    }
                    if (br != null) {
                        br.close();
                    }
                } catch (Exception e) {
                    Log.e("Crawler", e.toString());
                }
            }

            Log.e("doInBackgroud", "7");
            page.setContentText(response.toString());
            mVisitedLinks.add(mUrl);
            if (page.domainEqual(startPage)) {
                try {
                    Log.e("doInBackgroud", "8");
                    newLinks = page.findLinks();
                    newLinks.removeAll(mVisitedLinks);
                    Log.e("doInBackgroud", "9");
                } catch (Exception e) {
                    Log.e("Crawler", e.toString());
                }
            }
            if (newLinks == null){
                newLinks = new ArrayList<URL>();
            }
            Log.e("doInBackgroud", "10");
            savePage(page, page.equals(startPage));
            Log.e("doInBackgroud", "11");
            for (URL link : newLinks) {
                Page newPage = new Page(link);
                if (newPage.domainEqual(startPage)) {
                    // Enqueue a new Crawler
                    try {
                        Log.e("doInBackgroud", "12");
                        mTaskQueue.add(new Crawler(link));
                    } catch (Exception e) {
                        Log.e("Crawler", e.toString());
                    }
                }
            }
            // Unlock the main thread
            lock.lock();
            if (mTaskQueue.size() > 0) taskNotEmpty.signal();
            lock.unlock();

            Log.e("doInBackgroud", "13");
            // Dequeue itself from mOnRunQueue when finish running
            mOnRunQueue.remove(this);
            Log.e("doInBackgroud", "14");
            return page.getContentText();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
        }

        @Override
        protected void onPostExecute(String result) {
        }
    }

    protected void savePage(Page page, boolean isMain) {
        ContentValues values = new ContentValues();
        values.put("title", page.extractTitle());
        values.put("url", page.getPageUrl().toString());
        values.put("domain", page.extractDirectory());
        values.put("is_main", isMain);
        values.put("content", page.getContentText());
        int colNum = mDB.update(DB_TABLE, values, "url=?", new String[]{page.getPageUrl().toString()});
        if (colNum == 0) {
            mDB.insert(DB_TABLE, "", values);
        }
    }

    public void crawlPages(String startUrl) {
        mVisitedLinks = new ArrayList<URL>();
        try {
            startPage = new Page(new URL(startUrl));
        } catch (MalformedURLException e) {
            return;
        } catch (Exception e) {
            Log.e("crawlPage", e.toString());
            return;
        }

        try {
            mTaskQueue.add(new Crawler(startPage.getPageUrl()));
        } catch (Exception e) {
            Log.e("crawlPages", e.toString());
        }

        Log.e("crawlPages", "0");
        while (mTaskQueue.size() > 0 || mOnRunQueue.size() > 0) {
            try {
                Log.e("crawlPages", "1");
                // Wait if there is no task
                lock.lock();
                if (mTaskQueue.size() == 0) taskNotEmpty.await();
                lock.unlock();
                Crawler crawler = mTaskQueue.remove(0);
                Log.e("crawlPages", "2");
                mOnRunQueue.put(crawler);
                Log.e("crawlPages", "3");
                crawler.execute();
                Log.e("crawlPages", "4");
            } catch (Exception e) {
                Log.e("crawlPages", e.toString());
            }
        }
    }

    public void cancelTask() { stop_flag = true; }
}
