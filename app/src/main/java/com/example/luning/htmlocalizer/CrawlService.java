package com.example.luning.htmlocalizer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by luning on 2016/04/03.
 */
public class CrawlService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private static final int JOB_MAX_LIMIT = 80;
    private static final int CRAWLED_AMOUNT_LIMIT = 6000;
    private static long PAGE_TRANSACTION_BATCH_SIZE = 200;
    private static long IMAGE_TRANSACTION_BATCH_SIZE = 500;
    private SQLiteDatabase mDB;
    private DBTransactionManager<PageDBHelper.PageRecord> mPageDBTM;
    private DBTransactionManager<PageDBHelper.ImageRecord> mImageDBTM;
    private Page mStartPage;
    private String mStartPageTitle;
    private boolean stop_flag = true;
    private ArrayList<Crawler> mTaskQueue = new ArrayList<Crawler>();
    private ArrayBlockingQueue<Crawler> mOnRunQueue = new ArrayBlockingQueue<Crawler>(JOB_MAX_LIMIT);
    private ArrayList<URL> mVisitedLinks;
    private final Lock lock = new ReentrantLock();
    private final Lock ntfLock = new ReentrantLock();
    private final Condition taskNotFull = lock.newCondition(),
        taskNotEmpty = lock.newCondition(),
        taskNotTooMany = lock.newCondition();
    private ThreadPoolExecutor mCrawlExecutor;

    private static int CORE_POOL_SIZE = 4, MAX_POOL_SIZE = 8;
    private static long KEEP_ALIVE_TIME = 5000;

    public class LocalBinder extends Binder {
        CrawlService getService() {
            return CrawlService.this;
        }
    }

    @Override
    public void onCreate() {
        // Get database object
        PageDBHelper dbHelper = new PageDBHelper(this);
        mDB = dbHelper.getWritableDatabase();
        mPageDBTM = new DBTransactionManager<PageDBHelper.PageRecord>(mDB);
        mImageDBTM = new DBTransactionManager<PageDBHelper.ImageRecord>(mDB);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {

    }

    public void crawlPages(final String startUrl) {
        mCrawlExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.MICROSECONDS,
                new LinkedBlockingQueue<Runnable>());

        Runnable crawlController = new Runnable() {
            @Override
            public void run() {
                try {
                    mPageDBTM.initializeTransaction(PageDBHelper.PAGE_TRANSACTION_SQL);
                } catch (Exception e) {
                    Log.e("crawlPages", e.toString());
                    return;
                }
                try {
                    mImageDBTM.initializeTransaction(PageDBHelper.IMAGE_TRANSACTION_SQL);
                } catch (Exception e) {
                    Log.e("crawlPages", e.toString());
                    return;
                }
                mVisitedLinks = new ArrayList<URL>();
                try {
                    mStartPage = new Page(new URL(startUrl));
                } catch (MalformedURLException e) {
                    Log.e("crawlPage", e.toString());
                    return;
                } catch (Exception e) {
                    Log.e("crawlPage", e.toString());
                    return;
                }

                try {
                    mCrawlExecutor.execute(new Crawler(mStartPage.getPageUrl()));
                } catch (Exception e) {
                    Log.e("crawlPages", e.toString());
                }

                // Wait till crawling process ends
                do {
                    try {
                        synchronized (this) {
                            wait(4000);
                        }
                    } catch (Exception e) { Log.e("crawlPages", e.toString()); }
                } while (mCrawlExecutor.getQueue().size() > 0);


                Notification.Builder nftBuilder = notificationBuilderFactory("HtmLocalizer - " +
                        mStartPageTitle, "Crawling completed. Saving...", R.mipmap.ic_launcher);
                nftBuilder.setProgress(0, 0, true);
                showNotification(R.layout.activity_main, nftBuilder);

                // Save pages to database
                try {
                    mPageDBTM.commitTransaction(false);
                } catch (Exception e) {
                    Log.e("crawlPages", e.toString());
                }

                nftBuilder = notificationBuilderFactory("HtmLocalizer - " + mStartPageTitle,
                        "Pages saved. Saving images...", R.mipmap.ic_launcher);
                nftBuilder.setProgress(0, 0, true);
                showNotification(R.layout.activity_main, nftBuilder);

                // Save images to database
                try {
                    mImageDBTM.commitTransaction(false);
                } catch (Exception e) {
                    Log.e("crawlPages", e.toString());
                }

                nftBuilder = notificationBuilderFactory("HtmLocalizer - " + mStartPageTitle,
                        "Localizing completed.", R.mipmap.ic_launcher);
                showNotification(R.layout.activity_main, nftBuilder);
            }
        };
        mCrawlExecutor.execute(crawlController);
    }

    protected Notification.Builder notificationBuilderFactory(
            String title, CharSequence text, int icon) {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(icon);
        return builder;
    }


    private class Crawler implements Runnable {
        private URL mUrl;
        private ArrayList<URL> newLinks = null;

        public Crawler(URL url) {
            mUrl = url;
        }

        @Override
        public void run() {
            Page page = new Page(mUrl);
            // Crawled too many or already visited
            if (mVisitedLinks.size() > CRAWLED_AMOUNT_LIMIT || mVisitedLinks.contains(page.getPageUrl())) {
                return;
            }
            Log.e("visitedLinks", String.valueOf(mVisitedLinks.size()));
            mVisitedLinks.add(mUrl);

            // Get file via network and save it
            if (Page.isImageURL(mUrl.toString())) {
                // If url designates a image
                byte[] image_data = httpGetImage(mUrl);
                try {
                    mImageDBTM.addRecord(new PageDBHelper.ImageRecord(mUrl.toString(),
                            mStartPage.extractDirectory(), image_data));
                } catch (Exception e) { Log.e("Crawler", e.toString()); }
            } else {
                // If url designates a normal page
                page.setContentText(httpGetRawText(mUrl));
                // Set start page title (Name of a website)
                if (page.equals(mStartPage)) mStartPageTitle = page.extractTitle();

                try {
                    mPageDBTM.addRecord(new PageDBHelper.PageRecord(page.extractTitle(),
                            page.getPageUrl().toString(), mStartPage.extractDirectory(),
                            page.equals(mStartPage), page.getContentText()));
                } catch (Exception e) { Log.e("Crawler", e.toString()); }
            }

            // Notify status
            long crawledAmount = mVisitedLinks.size();
            if (crawledAmount % 10 == 0) {
                CharSequence ntfMsg = "Crawled " + crawledAmount + " pages.";
                Notification.Builder nftBuilder = notificationBuilderFactory(
                        "HtmLocalizer - " + mStartPageTitle, ntfMsg, R.mipmap.ic_launcher);
                nftBuilder.setProgress(0, 0, true);
                showNotification(R.layout.activity_main, nftBuilder);
            }

            // Find new links
            if (page.domainEqual(mStartPage) && (Page.isPageURL(mUrl.toString()) ||
                Page.isJsURL(mUrl.toString()) || Page.isCSSURL(mUrl.toString()))) {
                try {
                    newLinks = page.findLinks();
                    newLinks.removeAll(mVisitedLinks);
                } catch (Exception e) {
                    Log.e("Crawler", e.toString());
                }
            }
            newLinks = (newLinks == null) ? new ArrayList<URL>() : newLinks;
            // Log.e("newLinks-"+mUrl.toString(), newLinks.toString());

            if (mPageDBTM.getRecordNum() > PAGE_TRANSACTION_BATCH_SIZE) {
                try {
                    mPageDBTM.commitTransaction(true);
                } catch (Exception e) {
                    Log.e("Crawler", e.toString());
                }
            }

            if (mImageDBTM.getRecordNum() > IMAGE_TRANSACTION_BATCH_SIZE) {
                try {
                    mImageDBTM.commitTransaction(true);
                } catch (Exception e) {
                    Log.e("Crawler", e.toString());
                }
            }

            // Check new links
            if (page.domainEqual(mStartPage)) {
                for (URL link : newLinks) {
                    Page newPage = new Page(link);
                    // Enqueue a new Crawler
                    try {
                        mCrawlExecutor.execute(new Crawler(link));
                    } catch (Exception e) {
                        Log.e("Crawler", e.toString());
                    }
                }
            }
        }
    }


    protected byte[] httpGetImage(URL url) {
        HttpURLConnection con = null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            con = getConnectionFactory(url);
            InputStream is = con.getInputStream();
            int readLen;
            byte[] data = new byte[1024];
            while ((readLen = is.read(data, 0 , data.length)) != -1) {
                buffer.write(data, 0, readLen);
            }
            buffer.flush();
        } catch (Exception e) {
            Log.e("httpGetImage", e.toString());
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
        return buffer.toByteArray();
    }

    protected String httpGetRawText(URL url) {
        HttpURLConnection con = null;
        try {
            con = getConnectionFactory(url);
            return inputStream2String(con.getInputStream());
        } catch (Exception e) {
            Log.e("httpGetRawText", e.toString());
            return "";
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    protected HttpURLConnection getConnectionFactory(URL url)
            throws IOException, ProtocolException{
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setInstanceFollowRedirects(true);
        con.setDoInput(true);
        con.connect();
        return con;
    }

    protected String inputStream2String(InputStream is) {
        BufferedReader br = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            br = new BufferedReader(new InputStreamReader(is));
            String str;
            while ((str = br.readLine()) != null) { stringBuilder.append(str); }
        } catch (Exception e) {
            Log.e("inputStream2String", e.toString());
        } finally {
            try {
                if (br != null) { br.close(); }
            } catch (Exception e) { Log.e("inputStream2String", e.toString()); }
        }
        return stringBuilder.toString();
    }

    protected void showNotification(int notificationId, Notification.Builder builder) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        builder.setContentIntent(PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT));
        NotificationManager notificationManager =
                (NotificationManager)this.getSystemService(this.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
        notificationManager.notify(notificationId, builder.build());
    }

    public void cancelTask() { stop_flag = true; }
}
