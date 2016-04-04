package com.example.luning.htmlocalizer;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.ExecutorDelivery;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public final static String FILE_LIST = "SAVED_FILE_LIST";
    private final String homePage = "https://www.google.co.jp/";
    private final Handler mHandler = new Handler();
    private Context mContext;
    private Spinner savedPageSpinner;
    private Button goBtn, crawlBtn, backBtn, forwardBtn;
    private EditText urlBox;
    private ArrayList<StringAndMeta> spinnerTitleList;
    private ArrayAdapter<StringAndMeta> spinnerArrayAdapter;
    private WebView mWebView;
    private RequestQueue mRequestQueue;
    private Boolean runSearch = false;
    private long saveAmountLimit = 500, crawledAmountLimit = 500;
    private long crawledAndSavedPages = 0;
    private Intent crawlServiceIntent;
    private CrawlService mCrawlService;

    private static final String DB_NAME = "PAGES_DB";
    private static final String DB_TABLE = "PAGES_CONTENT_TABLE";
    private static final int DB_VERSION = 1;
    private SQLiteDatabase mDB;



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

    private static class StringAndMeta {
        private String mTitle, mMeta;

        public StringAndMeta(String title, String meta) {
            mTitle = title;
            mMeta = meta;
        }

        public String getTitle() {
            return mTitle;
        }

        public void setTitle(String title) {
            this.mTitle = mTitle;
        }

        public String getMeta() {
            return mMeta;
        }

        public void setMeta(String meta) {
            this.mMeta = mMeta;
        }

        @Override
        public String toString() {
            return mTitle;

        }
    }




    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        // Create RequestQueue
        mRequestQueue = new Volley().newRequestQueue(MainActivity.this);

        // Get database object
        DBHelper dbHelper = new DBHelper(this);
        mDB = dbHelper.getWritableDatabase();

        // Prepare for crawl service
        Intent intent = new Intent(getApplicationContext(), CrawlService.class);
        bindService(intent, mCrawlServiceConnection, Context.BIND_AUTO_CREATE);

        // Read the list of saved pages
        new AsyncTask<String, Void, ArrayList<StringAndMeta>>() {
            @Override
            protected ArrayList<StringAndMeta> doInBackground(String... params) {
                return getMainPages();
            }

            @Override
            protected void onPostExecute(ArrayList<StringAndMeta> titles) {
                spinnerTitleList = new ArrayList<StringAndMeta>(titles);
                spinnerTitleList.add(0, new StringAndMeta("Home", homePage));
                spinnerTitleList.add(0, new StringAndMeta("", ""));
                spinnerArrayAdapter = new ArrayAdapter<StringAndMeta>(
                        mContext, R.layout.spinner_item, spinnerTitleList);
                spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                savedPageSpinner = (Spinner)findViewById(R.id.saved_pages_spr);
                savedPageSpinner.setAdapter(spinnerArrayAdapter);
                savedPageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        if (pos == 0) {
                            // Do nothing
                        } else if (pos == 1) {
                            mWebView.loadUrl(((StringAndMeta) parent.getItemAtPosition(pos)).getMeta());
                        } else {
                            StringAndMeta pm = (StringAndMeta) parent.getItemAtPosition(pos);
                            String strHtml = getContent(pm.getMeta());
                            mWebView.loadDataWithBaseURL(pm.getMeta(), strHtml, "text/html", "UTF-8", "");
                            //mWebView.loadData(strHtml, "text/html", "UTF-8");
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
            }
        }.execute(FILE_LIST);

        // Create web view
        mWebView = (WebView)findViewById(R.id.webView);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);
        settings.setSupportZoom(true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String pageContent = getContent(url);
                if (pageContent.equals("")) {
                    return false;
                } else {
                    mWebView.loadDataWithBaseURL(url, pageContent, "text/html", "UTF-8", null);
                    return true;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // Set accurate url on load complete
                urlBox.setText(url);
                // Disable back button if unable to go back
                backBtn.setEnabled(view.canGoBack());
                // Disable forward button if unable to go forward
                forwardBtn.setEnabled(view.canGoForward());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
            }
        });
        mWebView.loadUrl(homePage);

        goBtn = (Button)findViewById(R.id.goBtn);
        urlBox = (EditText)findViewById(R.id.urlBox);
        urlBox.setOnKeyListener(new View.OnKeyListener() {
            // Enter key = Go
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String strUrl = urlBox.getText().toString();
                    if (!strUrl.equals("")) {
                        mWebView.loadUrl(completeURL(strUrl));
                    }
                    return true;
                }
                return false;
            }
        });

        urlBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (urlBox.getText().toString().equals("")) {
                        urlBox.setText(mWebView.getUrl());
                    }
                    urlBox.selectAll();
                }
            }
        });

        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String strUrl = urlBox.getText().toString();
                if (strUrl.equals("")) {
                    Toast.makeText(MainActivity.this, "Please specify a URL.", Toast.LENGTH_SHORT);
                    return;
                } else {
                    mWebView.loadUrl(completeURL(strUrl));
                }
            }
        });

        crawlBtn = (Button)findViewById(R.id.crawlBtn);
        crawlBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startUrl = urlBox.getText().toString();
                if (startUrl.equals("")) {
                    startUrl = mWebView.getUrl();
                }
                try {
                    mCrawlService.crawlPages(startUrl);
                } catch (Exception e) {
                    Log.e("onCrawlPressed", e.toString());
                }

                //runSearch = true;
                /*
                crawledAndSavedPages = 0;
                ArrayList<URL> visitedList = new ArrayList<URL>();
                Log.e("crawlClick", String.valueOf(visitedList.size()));
                try {
                    Log.e("crawlClick", "CRAWL button pressed");
                    String strStartUrl = urlBox.getText().toString();
                    if (strStartUrl.equals("")) {
                        strStartUrl = mWebView.getUrl();
                    }
                    URL startURL = new URL(strStartUrl);
                    Page startPage = new Page(startURL);
                    crawl(startPage, startPage, visitedList);
                } catch (Exception e) {
                    Log.e("crawlClick", e.toString());
                }
                */
            }
        });

        backBtn = (Button)findViewById(R.id.backBtn);
        backBtn.setEnabled(false);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.goBack();
            }
        });

        forwardBtn = (Button)findViewById(R.id.forwardBtn);
        forwardBtn.setEnabled(false);
        forwardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.goForward();
            }
        });
    }

    private ServiceConnection mCrawlServiceConnection  = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("service", "Service connected.");
            CrawlService.LocalBinder binder = (CrawlService.LocalBinder)service;
            mCrawlService = binder.getService();


        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        // Prepare for crawling service
        //Intent intent = new Intent(getApplicationContext(), CrawlService.class);
        //bindService(intent, mCrawlServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        // Destroy service
        unbindService(mCrawlServiceConnection);
        super.onDestroy();
    }


    protected String completeURL(String strURL) {
        Pattern hrefPattern = Pattern.compile("http://|https://");
        Matcher matcher = hrefPattern.matcher(strURL);
        if (!matcher.find()) {
            strURL = "http://" + strURL;
        }
        return strURL;
    }

    protected void crawl(final Page page, final Page orgPage, final ArrayList<URL> visitedLinks) {
        if (crawledAndSavedPages > saveAmountLimit || visitedLinks.size() > crawledAmountLimit) {
            return;
        }
        Log.e("visitedLinks", String.valueOf(visitedLinks.size()));
        if (visitedLinks.indexOf(page.getPageUrl()) >= 0) {
            Log.e("crawl", "Already visited.");
            return;
        }
        visitedLinks.add(page.getPageUrl());
        mRequestQueue.add(new StringRequest(Request.Method.GET, page.getPageUrl().toString(),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(final String response) {
                        page.setContentText(response);
                        savePage(page, page.equals(orgPage));
                        Log.e("savedPages", String.valueOf(++crawledAndSavedPages));
                        if (page.domainEqual(orgPage)) {
                            ArrayList<URL> newLinks;
                            try {
                                newLinks = page.findLinks();
                            } catch (Exception e) {
                                Log.e("crawl_onResponse", e.toString());
                                newLinks = new ArrayList<URL>();
                            }
                            newLinks.removeAll(visitedLinks);
                            for (URL link : newLinks) {
                                Page newPage = new Page(link);
                                if (newPage.domainEqual(orgPage)) {
                                    crawl(newPage, orgPage, visitedLinks);
                                }
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("crawl_onError", "Request failed. " + error.toString());
                    }
                }).setShouldCache(false));
    }


    protected void modifySpinner() {
        spinnerTitleList = getMainPages();
        spinnerTitleList.add(new StringAndMeta("Home", homePage));
        spinnerTitleList.add(new StringAndMeta("", ""));
        spinnerArrayAdapter.notifyDataSetChanged();
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

    protected String getContent(String strUrl) {
        Cursor cursor = getPageInfo(strUrl, new String[]{"content"});
        String content;
        if (cursor == null) {
            content = "";
        } else {
            cursor.moveToFirst();
            content = cursor.getString(0);
            cursor.close();
        }
        return content;
    }

    protected String getTitle(String strUrl) {
        Cursor cursor = getPageInfo(strUrl, new String[]{"title"});
        String content;
        if (cursor == null) {
            content = "";
        } else {
            cursor.moveToFirst();
            content = cursor.getString(0);
            cursor.close();
        }
        return content;
    }

    protected Cursor getPageInfo(String strUrl, String[] item) {
        Cursor cursor = mDB.query(DB_TABLE, item,
                "url=?", new String[]{strUrl}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected Cursor getPageInfo(String strUrl) {
        Cursor cursor = mDB.query(DB_TABLE, null,
                "url=?", new String[]{strUrl}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected Cursor getPageInfo(int id) {
        Cursor cursor = mDB.query(DB_TABLE, null,
                "id=?", new String[]{String.valueOf(id)}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected ArrayList<String> getMainPageUrls() {
        ArrayList<String> mainPages = new ArrayList<String>();
        Cursor cursor = mDB.query(DB_TABLE, new String[]{"url"}, "is_main=true", null, null, null, "1");
        while (cursor.moveToNext()) {
            mainPages.add(new String(cursor.getString(0)));
        }
        return mainPages;
    }

    protected ArrayList<String> getMainPageTitles() {
        ArrayList<String> mainPages = new ArrayList<String>();
        Cursor cursor = mDB.query(DB_TABLE, new String[]{"title"}, "is_main=?", new String[]{"1"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mainPages.add(new String(cursor.getString(0)));
        }
        return mainPages;
    }

    protected ArrayList<StringAndMeta> getMainPages() {
        ArrayList<StringAndMeta> mainPages = new ArrayList<StringAndMeta>();
        Cursor cursor = mDB.query(DB_TABLE, new String[]{"title", "url"}, "is_main=?", new String[]{"1"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mainPages.add(new StringAndMeta(cursor.getString(0), cursor.getString(1)));
        }
        return mainPages;
    }










}
