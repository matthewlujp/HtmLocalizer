package com.example.luning.htmlocalizer;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
        implements View.OnKeyListener, View.OnFocusChangeListener,
        CrawlResultReceiver.Receiver, View.OnClickListener, RecyclerView.OnItemTouchListener {
    public final static String FILE_LIST = "SAVED_FILE_LIST";
    private static final int TAG_GO_BUTTON = 0;
    private static final int TAG_CRAWL_BUTTON = 1;
    private static final int TAG_BACK_BUTTON = 2;
    private static final int TAG_FORWARD_BUTTON = 3;
    private static final int TAG_REFRESH_BUTTON = 4;
    private static final String mHomePage = "https://www.google.co.jp/";
    private ImageButton backBtn, forwardBtn, crawlBtn, goBtn, refreshBtn;
    private EditText urlBox;
    private final ArrayList<SiteListItem> mTitleList = new ArrayList<SiteListItem>();
    private DrawerListAdapter mDrawerArrayAdapter;
    private WebView mWebView;
    private SQLiteDatabase mDB;
    private CrawlResultReceiver mCrawlResultReceiver;
    private DrawerLayout mDrawerLayout;
    private RecyclerView mRecyclerView;
    private ActionBarDrawerToggle mDrawerToggle;
    private Toolbar mToolbar;
    private GestureDetector mSingleTapDetector;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get database object
        PageDBHelper dbHelper = new PageDBHelper(this);
        mDB = dbHelper.getWritableDatabase();

        mSingleTapDetector = new GestureDetector(this, mSimpleOnGestureListener);
        mToolbar = (Toolbar)findViewById(R.id.actionbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mDrawerToggle = actionBarDrawerToggleFactory(mDrawerLayout, mToolbar);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerArrayAdapter = new DrawerListAdapter(mTitleList);
        mRecyclerView = (RecyclerView)findViewById(R.id.drawer_list);
        mRecyclerView.setAdapter(mDrawerArrayAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addOnItemTouchListener(this);
        mDrawerToggle.syncState();

        siteListCreator.execute(FILE_LIST);

        // Create web view
        mWebView = (WebView)findViewById(R.id.webView);
        configureWebView(mWebView);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.loadUrl(mHomePage);

        // Buttons
        urlBox = (EditText)findViewById(R.id.urlBox);
        urlBox.setOnKeyListener(this);
        urlBox.setOnFocusChangeListener(this);
        goBtn = (ImageButton)findViewById(R.id.goBtn);
        goBtn.setTag(TAG_GO_BUTTON);
        goBtn.setOnClickListener(this);
        crawlBtn = (ImageButton)findViewById(R.id.crawlBtn);
        crawlBtn.setTag(TAG_CRAWL_BUTTON);
        crawlBtn.setOnClickListener(this);
        backBtn = (ImageButton)findViewById(R.id.backBtn);
        backBtn.setEnabled(false);
        backBtn.setTag(TAG_BACK_BUTTON);
        backBtn.setOnClickListener(this);
        forwardBtn = (ImageButton)findViewById(R.id.forwardBtn);
        forwardBtn.setEnabled(false);
        forwardBtn.setTag(TAG_FORWARD_BUTTON);
        forwardBtn.setOnClickListener(this);
        refreshBtn = (ImageButton)findViewById(R.id.refreshBtn);
        refreshBtn.setTag(TAG_REFRESH_BUTTON);
        refreshBtn.setOnClickListener(this);


        mCrawlResultReceiver = new CrawlResultReceiver(new Handler());
        mCrawlResultReceiver.setReceiver(this);
    }


    @Override
    public void onDestroy() {
        // Destroy service
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    // ----------------------------- Listeners -----------------------------
    // OnClickListener
    @Override
    public void onClick(View view){
        int tag = (int)view.getTag();
        String strUrl = urlBox.getText().toString();
        switch (tag) {
            case TAG_GO_BUTTON:
                if (strUrl.equals("")) {
                    Toast.makeText(MainActivity.this, "Please specify a URL.", Toast.LENGTH_SHORT);
                    return;
                } else {
                    mWebView.loadUrl(completeURL(strUrl));
                }
                break;
            case TAG_CRAWL_BUTTON:
                if (strUrl.equals("")) {
                    strUrl = mWebView.getUrl();
                }
                try {
                    Intent intent = new Intent(MainActivity.this, CrawlService.class);
                    intent.putExtra(CrawlService.START_URL_TAG, strUrl);
                    intent.putExtra("receiver", mCrawlResultReceiver);
                    startService(intent);
                } catch (Exception e) {
                    Log.e("onCrawlPressed", e.toString());
                }
                break;
            case TAG_BACK_BUTTON:
                mWebView.goBack();
                break;
            case TAG_FORWARD_BUTTON:
                mWebView.goForward();
                break;
            case TAG_REFRESH_BUTTON:
                mWebView.loadUrl(mWebView.getUrl());
                break;
        }
    }

    // OnKeyListener
    @Override
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

    // OnFocusChangedListener
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            if (urlBox.getText().toString().equals("")) {
                urlBox.setText(mWebView.getUrl());
            }
            urlBox.selectAll();
        }
    }

    // CrawlResultReceiver.Receiver
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        if (resultCode == RESULT_OK) {
            String msg;
            switch (resultData.getInt(CrawlService.CURRENT_STATUS)) {
                case CrawlService.PAGES_COMPLETED:
                    // Log.e("onResultReceive", "page ok");
                    modifyMenu();
                    msg = "Localizing pages completed.";
                    break;
                case CrawlService.IMAGES_COMPLETED:
                    msg = "Localizing pages completed.";
                    break;
                case CrawlService.LOCALIZING_FAILED:
                    msg = "Localizing failed.";
                    break;
                default:
                    msg = "Something happened.";
            }
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // RecyclerView.OnItemTouchListener
    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        View child = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
        if (child != null && mSingleTapDetector.onTouchEvent(e)) {
            int position = mRecyclerView.getChildLayoutPosition(child);
            if (position == 0) {
                mWebView.loadUrl(mDrawerArrayAdapter.getItem(position).getURL());
            } else {
                SiteListItem pm = mDrawerArrayAdapter.getItem(position);
                String strHtml = getContent(pm.getURL());
                mWebView.loadDataWithBaseURL(pm.getURL(), strHtml, "text/html", "UTF-8", "");
            }
            mDrawerLayout.closeDrawers();
            return true;
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }


    // ----------------------------- Utility methods -----------------------------
    protected void configureWebView(WebView webView) {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);
        settings.setSupportZoom(true);
    }

    protected String completeURL(String strURL) {
        Pattern hrefPattern = Pattern.compile("http://|https://");
        Matcher matcher = hrefPattern.matcher(strURL);
        if (!matcher.find()) {
            strURL = "http://" + strURL;
        }
        return strURL;
    }

    protected void modifyMenu() {
        mTitleList.clear();
        mTitleList.addAll(getMainPages());
        mTitleList.add(0, new SiteListItem("Home", mHomePage));
        mDrawerArrayAdapter.notifyDataSetChanged();
    }

    protected void modifyMenu(ArrayList<SiteListItem> items) {
        mTitleList.clear();
        mTitleList.addAll(items);
        mTitleList.add(0, new SiteListItem("Home", mHomePage));
        mDrawerArrayAdapter.notifyDataSetChanged();
    }

    protected String getContent(String strUrl) {
        Cursor cursor = getPageCursor(strUrl, new String[]{"content"});
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

    public Cursor getPageCursor(String url, String[] item) throws IllegalStateException {
        return accessDB(url, PageDBHelper.DB_PAGE_TABLE, item);
    }

    public Cursor getImageCursor(String url, String[] item) throws IllegalStateException {
        return accessDB(url, PageDBHelper.DB_IMAGE_TABLE, item);
    }

    public Cursor accessDB(String url, String tableName, String[] item)
            throws IllegalStateException {
        if (mDB == null) throw new IllegalStateException("DB hasn't setup properly, yet!");
        Cursor cursor = mDB.query(tableName, item,
                "url=?", new String[]{url}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected ArrayList<SiteListItem> getMainPages() {
        ArrayList<SiteListItem> mainPages = new ArrayList<SiteListItem>();
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, new String[]{"title", "url"}, "is_main=?", new String[]{"1"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mainPages.add(new SiteListItem(cursor.getString(0), cursor.getString(1)));
        }
        return mainPages;
    }


    // Create callback for Navigation Drawer
    protected ActionBarDrawerToggle actionBarDrawerToggleFactory(
            DrawerLayout layout, Toolbar toolbar) {
        return new ActionBarDrawerToggle(
                MainActivity.this, layout, toolbar,
                R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

        };
    }


    // Carries out loading main pages of localized websites
    private final AsyncTask<String, Void, ArrayList<SiteListItem>> siteListCreator
            = new AsyncTask<String, Void, ArrayList<SiteListItem>>() {
        @Override
        protected ArrayList<SiteListItem> doInBackground(String... params) {
            return getMainPages();
        }

        @Override
        protected void onPostExecute(ArrayList<SiteListItem> items) {
            modifyMenu(items);

        }
    };


    // For detection of motion on RecyclerView
    private GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener =
            new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onSingleTapUp(MotionEvent e) { return true; }
    };


    // For configuring behavior of WebView, create WebViewClient
    private WebViewClient mWebViewClient = new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String pageContent = getContent(url);
            if (mWebView == null || pageContent.equals("")) {
                return false;
            } else {
                mWebView.loadDataWithBaseURL(url, pageContent, "text/html", "UTF-8", null);
                return true;
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            urlBox.setText(url);
            backBtn.setEnabled(view.canGoBack());
            forwardBtn.setEnabled(view.canGoForward());
        }

        @Override
        @Nullable
        public WebResourceResponse shouldInterceptRequest(WebView view,
                                                          WebResourceRequest request) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString());
            Cursor cursor = null;
            WebResourceResponse response = null;
            try {
                String url = request.getUrl().toString();
                byte[] byteData = null;
                if (extension.equals("css") || extension.equals("js")) {
                    cursor = getPageCursor(url, new String[]{"content"});
                    byteData = cursor.getString(0).getBytes("UTF-8");
                } else if (extension.equals("jpeg") || extension.equals("jpg") ||
                        extension.equals("JPG") || extension.equals("png")) {
                    cursor = getImageCursor(url, new String[]{"image"});
                    byteData = cursor.getBlob(0);
                }
                response = byteArray2WebResponse(url, byteData);
            } catch (Exception e) {
                //Log.e("shouldIntercept", e.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return response;
        }

        @Nullable
        protected WebResourceResponse byteArray2WebResponse(String url, byte[] data) {
            try {
                return new WebResourceResponse(getMimeType(url),
                        "UTF-8", new ByteArrayInputStream(data));
            } catch (Exception e) {
                //Log.e("WebViewClient", e.toString());
            }
            return null;
        }

        public String getMimeType(String url) {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                if (extension.equals("js")) {
                    return "text/javascript";
                } else if (extension.equals("jpg") || extension.equals("jpeg") || extension.equals("JPG")) {
                    return "image/*";
                } else if (extension.equals("png")) {
                    return "image/*";
                } else if (extension.equals("svg")) {
                    return "image/svg+xml";
                }
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        }
    };

}
