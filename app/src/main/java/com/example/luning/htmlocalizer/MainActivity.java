package com.example.luning.htmlocalizer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.NavigationView;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public final static String FILE_LIST = "SAVED_FILE_LIST";
    private final String homePage = "https://www.google.co.jp/";
    private ImageButton backBtn, forwardBtn, crawlBtn, goBtn;
    private EditText urlBox;
    private ArrayList<StringAndMeta> mTitleList;
    private DrawerListAdapter mDrawerArrayAdapter;
    private WebView mWebView;
    private SQLiteDatabase mDB;
    private CrawlResultReceiver mCrawlResultReceiver;
    private DrawerLayout mDrawerLayout;
    private RecyclerView mRecyclerView;
    private ActionBarDrawerToggle mDrawerToggle;
    private LinearLayoutManager mLayoutManager;
    private ListView mDrawerList;
    private Toolbar mToolbar;


    public static class StringAndMeta {
        private String mTitle, mMeta;
        private Bitmap mIcon;

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

        // Get database object
        PageDBHelper dbHelper = new PageDBHelper(this);
        mDB = dbHelper.getWritableDatabase();

        mToolbar = (Toolbar)findViewById(R.id.actionbar);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mRecyclerView = (RecyclerView)findViewById(R.id.drawer_list);
        mDrawerToggle = new ActionBarDrawerToggle(
                MainActivity.this,
                mDrawerLayout,
                mToolbar,
                R.string.drawer_open,
                R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        final GestureDetector singleTapDetector = new GestureDetector(
                MainActivity.this, new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapUp(MotionEvent e) { return true; }
        });

        // Set Drawer Listener
        mDrawerLayout.addDrawerListener(mDrawerToggle);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Read the list of saved pages
        new AsyncTask<String, Void, ArrayList<StringAndMeta>>() {
            @Override
            protected ArrayList<StringAndMeta> doInBackground(String... params) {
                return getMainPages();
            }

            @Override
            protected void onPostExecute(ArrayList<StringAndMeta> titles) {
                mTitleList = new ArrayList<StringAndMeta>(titles);
                mTitleList.add(0, new StringAndMeta("Home", homePage));
                //mTitleList.add(0, new StringAndMeta("", ""));
                mDrawerArrayAdapter = new DrawerListAdapter(mTitleList);
                mRecyclerView.setAdapter(mDrawerArrayAdapter);
                mLayoutManager = new LinearLayoutManager(MainActivity.this);
                mRecyclerView.setLayoutManager(mLayoutManager);

                mRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                        View child = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
                        if (child != null && singleTapDetector.onTouchEvent(e)) {
                            int position = mRecyclerView.getChildLayoutPosition(child);
                            if (position == 0) {
                                mWebView.loadUrl(mDrawerArrayAdapter.getItem(position).getMeta());
                            } else {
                                StringAndMeta pm = mDrawerArrayAdapter.getItem(position);
                                String strHtml = getContent(pm.getMeta());
                                mWebView.loadDataWithBaseURL(pm.getMeta(), strHtml, "text/html", "UTF-8", "");
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
                });


                mDrawerToggle.syncState();


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

            @Override
            @Nullable
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString());
                if (extension.equals("css") || extension.equals("js")) {
                    return loadTextAssetsFromDB(request.getUrl().toString());
                } else if (extension.equals("jpeg") || extension.equals("jpg") ||
                        extension.equals("JPG") || extension.equals("png")) {
                    return loadImageAssetsFromDB(request.getUrl().toString());
                }
                return null;
            }

            @Nullable
            public WebResourceResponse loadTextAssetsFromDB(String url) {
                Cursor assetFileInfo = getPageCursor(url, new String[]{"content"});
                if (assetFileInfo != null && assetFileInfo.getCount() > 0) {
                    assetFileInfo.moveToFirst();
                    String assetText = assetFileInfo.getString(0);
                    assetFileInfo.close();
                    try {
                        return new WebResourceResponse(getMimeType(url),
                                "UTF-8", new ByteArrayInputStream(assetText.getBytes("UTF-8")));
                    } catch (Exception e) {
                        Log.e("WebViewClient", e.toString());
                    }
                }
                return null;
            }

            @Nullable
            public WebResourceResponse loadImageAssetsFromDB(String url) {
                Cursor assetFileInfo = getImageCursor(url, new String[]{"image"});
                if (assetFileInfo != null && assetFileInfo.getCount() > 0) {
                    assetFileInfo.moveToFirst();
                    byte[] imageData = assetFileInfo.getBlob(0);
                    assetFileInfo.close();
                    try {
                        return new WebResourceResponse(getMimeType(url),
                                "utf-8", new ByteArrayInputStream(imageData));
                    } catch (Exception e) {
                        Log.e("loadImageAssets", e.toString());
                    }
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
        });
        mWebView.loadUrl(homePage);

        goBtn = (ImageButton)findViewById(R.id.goBtn);
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

        mCrawlResultReceiver = new CrawlResultReceiver(new Handler());
        mCrawlResultReceiver.setReceiver(new CrawlResultReceiver.Receiver(){
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                Log.e("onResultReceive", String.valueOf(resultCode));
                if (resultCode == RESULT_OK) {
                    Log.e("onResultReceive", "OK");
                    String msg;
                    Log.e("onResultReceive", "status - " + String.valueOf(resultData.getInt(CrawlService.CURRENT_STATUS)));
                    switch (resultData.getInt(CrawlService.CURRENT_STATUS)) {
                        case CrawlService.PAGES_COMPLETED:
                            Log.e("onResultReceive", "page ok");
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

        });

        crawlBtn = (ImageButton)findViewById(R.id.crawlBtn);
        crawlBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startUrl = urlBox.getText().toString();
                if (startUrl.equals("")) {
                    startUrl = mWebView.getUrl();
                }
                try {
                    Intent intent = new Intent(MainActivity.this, CrawlService.class);
                    intent.putExtra(CrawlService.START_URL_TAG, startUrl);
                    intent.putExtra("receiver", mCrawlResultReceiver);
                    startService(intent);
                } catch (Exception e) {
                    Log.e("onCrawlPressed", e.toString());
                }
            }
        });

        backBtn = (ImageButton)findViewById(R.id.backBtn);
        backBtn.setEnabled(false);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.goBack();
            }
        });

        forwardBtn = (ImageButton)findViewById(R.id.forwardBtn);
        forwardBtn.setEnabled(false);
        forwardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.goForward();
            }
        });



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
        mTitleList.add(0, new StringAndMeta("Home", homePage));
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

    protected String getTitle(String strUrl) {
        Cursor cursor = getPageCursor(strUrl, new String[]{"title"});
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

    protected Cursor getPageCursor(String url, String[] item) {
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, item,
                "url=?", new String[]{url}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected Cursor getImageCursor(String url, String[] item) {
        Cursor cursor = mDB.query(PageDBHelper.DB_IMAGE_TABLE, item,
                "url=?", new String[]{url}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected Cursor getPageCursor(String strUrl) {
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, null,
                "url=?", new String[]{strUrl}, null, null, "1");
        if (cursor.getCount() == 0) {
            return null;
        } else {
            cursor.moveToFirst();
            return cursor;
        }
    }

    protected Cursor getPageCursor(int id) {
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, null,
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
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, new String[]{"url"}, "is_main=true", null, null, null, "1");
        while (cursor.moveToNext()) {
            mainPages.add(new String(cursor.getString(0)));
        }
        return mainPages;
    }

    protected ArrayList<String> getMainPageTitles() {
        ArrayList<String> mainPages = new ArrayList<String>();
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, new String[]{"title"}, "is_main=?", new String[]{"1"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mainPages.add(new String(cursor.getString(0)));
        }
        return mainPages;
    }

    protected ArrayList<StringAndMeta> getMainPages() {
        ArrayList<StringAndMeta> mainPages = new ArrayList<StringAndMeta>();
        Cursor cursor = mDB.query(PageDBHelper.DB_PAGE_TABLE, new String[]{"title", "url"}, "is_main=?", new String[]{"1"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mainPages.add(new StringAndMeta(cursor.getString(0), cursor.getString(1)));
        }
        return mainPages;
    }










}
