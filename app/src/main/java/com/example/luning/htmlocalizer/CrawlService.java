package com.example.luning.htmlocalizer;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



/**
 * Created by luning on 2016/04/03.
 */
public class CrawlService extends IntentService {
    //private final IBinder mBinder = new LocalBinder();
    private static int CORE_POOL_SIZE = 4, MAX_POOL_SIZE = 8;
    private static long KEEP_ALIVE_TIME = 5000;
    private static final int CRAWLED_AMOUNT_LIMIT = 6000;
    private static long PAGE_TRANSACTION_BATCH_SIZE = 200;
    private static long IMAGE_TRANSACTION_BATCH_SIZE = 500;
    private SQLiteDatabase mDB;
    private DBTransactionManager<PageDBHelper.PageRecord> mPageDBTM;
    private DBTransactionManager<PageDBHelper.ImageRecord> mImageDBTM;
    private Page mStartPage;
    private String mStartPageTitle;
    private ArrayList<URL> mVisitedLinks;
    private ThreadPoolExecutor mCrawlExecutor;
    private int mServiceId = 0;
    public static final String START_URL_TAG = "start_url";
    public static final String CURRENT_STATUS = "current_status";
    public static final int PAGES_COMPLETED = 1, IMAGES_COMPLETED = 2, LOCALIZING_FAILED = 3;
    private ResultReceiver mReceiver;

    public CrawlService() {
        super("CrawlService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Get database object
        PageDBHelper dbHelper = new PageDBHelper(this);
        mDB = dbHelper.getWritableDatabase();
        mPageDBTM = new DBTransactionManager<PageDBHelper.PageRecord>(mDB);
        mImageDBTM = new DBTransactionManager<PageDBHelper.ImageRecord>(mDB);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String startUrl = intent.getStringExtra(START_URL_TAG);
        mReceiver = intent.getParcelableExtra("receiver");
        mServiceId = 100;
        Notification.Builder builder = notificationBuilderFactory("HtmLocalizer - " + startUrl,
                "Initiate localizing...", R.mipmap.ic_launcher);
        showNotification(mServiceId, builder);

        crawlPages(startUrl);
    }

    public void crawlPages(final String startUrl) {
        mCrawlExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
                KEEP_ALIVE_TIME, TimeUnit.MICROSECONDS,
                new LinkedBlockingQueue<Runnable>());

        try {
            mPageDBTM.initializeTransaction(PageDBHelper.PAGE_TRANSACTION_SQL);
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
        showNotification(mServiceId, nftBuilder);

        // Save pages to database
        try {
            mPageDBTM.commitTransaction(false);
        } catch (Exception e) {
            Log.e("crawlPages", e.toString());
        }

        nftBuilder = notificationBuilderFactory("HtmLocalizer - " + mStartPageTitle,
                "Pages saved. Saving images...", R.mipmap.ic_launcher);
        nftBuilder.setProgress(0, 0, true);
        showNotification(mServiceId, nftBuilder);

        // Save images to database
        try {
            mImageDBTM.commitTransaction(false);
        } catch (Exception e) {
            Log.e("crawlPages", e.toString());
        }

        nftBuilder = notificationBuilderFactory("HtmLocalizer - " + mStartPageTitle,
                "Localizing completed.", R.mipmap.ic_launcher);
        showNotification(mServiceId, nftBuilder);

        // Notify MainActivity page localization completion
        Log.e("sendResult", "page");
        Bundle pageInfoBundle = new Bundle();
        pageInfoBundle.putInt(CURRENT_STATUS, PAGES_COMPLETED);
        mReceiver.send(Activity.RESULT_OK, pageInfoBundle);

        // Notify MainActivity image localization completion
        Log.e("sendResult", "image");
        Bundle imageInfoBundle = new Bundle();
        imageInfoBundle.putInt(CURRENT_STATUS, IMAGES_COMPLETED);
        mReceiver.send(Activity.RESULT_OK, imageInfoBundle);

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
            if (mVisitedLinks.size() > CRAWLED_AMOUNT_LIMIT ||
                    mVisitedLinks.contains(page.getPageUrl())) {
                return;
            }
            // Log.e("visitedLinks", String.valueOf(mVisitedLinks.size()));
            mVisitedLinks.add(mUrl);

            // Get file via network and save it
            if (Page.isImageURL(mUrl.toString())) {
                byte[] image_data = httpGetImage(mUrl);
                try {
                    mImageDBTM.addRecord(new PageDBHelper.ImageRecord(mUrl.toString(),
                            mStartPage.extractDirectory(), image_data));
                } catch (Exception e) { /* Log.e("Crawler", e.toString()); */ }
            } else {
                // If url designates a normal page
                page.setContentText(httpGetRawText(mUrl));
                // Set start page title (Name of a website)
                if (page.equals(mStartPage)) mStartPageTitle = page.extractTitle();

                try {
                    mPageDBTM.addRecord(new PageDBHelper.PageRecord(page.extractTitle(),
                            page.getPageUrl().toString(), mStartPage.extractDirectory(),
                            page.equals(mStartPage), page.getContentText()));
                } catch (Exception e) { /* Log.e("Crawler", e.toString()); */ }
            }

            // Notify status
            long crawledAmount = mVisitedLinks.size();
            if (crawledAmount % 10 == 0) {
                CharSequence ntfMsg = "Crawled " + crawledAmount + " pages.";
                Notification.Builder nftBuilder = notificationBuilderFactory(
                        "HtmLocalizer - " + mStartPageTitle, ntfMsg, R.mipmap.ic_launcher);
                nftBuilder.setProgress(0, 0, true);
                showNotification(mServiceId, nftBuilder);
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
            // Log.e("httpGetImage", e.toString());
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
            // Log.e("httpGetRawText", e.toString());
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
            // Log.e("inputStream2String", e.toString());
        } finally {
            try {
                if (br != null) { br.close(); }
            } catch (Exception e) { /* Log.e("inputStream2String", e.toString()); */ }
        }
        return stringBuilder.toString();
    }

    protected Notification.Builder notificationBuilderFactory(
            String title, CharSequence text, int icon) {
        Notification.Builder builder = new Notification.Builder(CrawlService.this);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(icon);
        return builder;
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

}
