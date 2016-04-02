package com.example.luning.htmlocalizer;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public final static String FILE_LIST = "SAVED_FILE_LIST";
    private final String homePage = "http://google.com";
    private final Handler mHandler = new Handler();
    private Context mContext;
    private Spinner savedPageSpinner;
    private Button goBtn, crawlBtn;
    private EditText urlBox;
    private ArrayList<String> savedFileNames, spinnerTitleList;
    private ArrayAdapter<String> spinnerArrayAdapter;
    private WebView mWebView;
    private RequestQueue mRequestQueue;
    private Boolean runSearch = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        // Create RequestQueue
        mRequestQueue = new Volley().newRequestQueue(MainActivity.this);

        // Read the list of saved pages
        new AsyncTask<String, Void, ArrayList<String>>() {
            @Override
            protected ArrayList<String> doInBackground(String... params) {
                ArrayList<String> fileNames = null;
                try {
                    File listFile = new File(mContext.getFilesDir(), FILE_LIST);
                    FileInputStream is = new FileInputStream(listFile);
                    ObjectInputStream ois = new ObjectInputStream(is);
                    fileNames = (ArrayList<String>) ois.readObject();
                    ois.close();
                    is.close();
                } catch (FileNotFoundException e) {
                    fileNames = new ArrayList<String>();
                } catch (EOFException e) {
                    Log.e("onCreate", e.toString());
                    fileNames = new ArrayList<String>();
                } catch (Exception e) {
                    Log.e("onCreate", e.toString());
                    System.exit(-1);
                }
                return fileNames;
            }

            @Override
            protected void onPostExecute(ArrayList<String> fileNames) {
                savedFileNames = fileNames;
                spinnerTitleList = new ArrayList<String>(fileNames);
                spinnerTitleList.add(0, "Home");
                spinnerTitleList.add(0, "");
                spinnerArrayAdapter = new ArrayAdapter<String>(
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
                            mWebView.loadUrl(homePage);
                        } else {
                            openPage(parent.getItemAtPosition(pos).toString());
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
            public boolean shouldOverrideUrlLoading(WebView view, String strUrl) {
                // Don't use external browser
                return false;
            }
        });
        mWebView.loadUrl(homePage);

        goBtn = (Button)findViewById(R.id.goBtn);
        urlBox = (EditText)findViewById(R.id.urlBox);

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
                //runSearch = true;
                ArrayList<URL> visitedList = new ArrayList<URL>();
                Log.e("crawlClick", String.valueOf(visitedList.size()));
                try {
                    Log.e("crawlClick", "CRAWL button pressed");
                    URL startURL = new URL(urlBox.getText().toString());
                    Page startPage = new Page(startURL);
                    crawl(startPage, startPage, visitedList);
                } catch (Exception e) {
                    Log.e("crawlClick", e.toString());
                }
            }
        });
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
                        String fileName = savePage(page);
                        pushSavedFileName(fileName);
                        modifySpinner(fileName);
                        if (orgPage.domainEqual(page)) {
                            ArrayList<URL> newLinks;
                            try {
                                newLinks = page.findLinks();
                            } catch (Exception e) {
                                Log.e("crawl_onResponse", e.toString());
                                newLinks = new ArrayList<URL>();
                            }
                            newLinks.removeAll(visitedLinks);
                            //Log.e("debug", "New links to check: " + newLinks.size() + " in total.");
                            //Log.e("debug", "Links removed visited: " + newLinks.toString());
                            for (URL link : newLinks) {
                                Page newPage = new Page(link);
                                if (orgPage.domainEqual(newPage)) {
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

    // Renew file name list
    protected void pushSavedFileName(String fileName) {
        savedFileNames.add(fileName);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                File listFile = new File(mContext.getFilesDir(), FILE_LIST);
                FileOutputStream os = null;
                ObjectOutputStream oos = null;
                try {
                    os = new FileOutputStream(listFile);
                    oos = new ObjectOutputStream(os);
                    oos.writeObject(savedFileNames);
                    oos.close();
                    os.close();
                } catch (Exception e) {
                    Log.e("pushSavedFileName", e.toString());
                } finally {
                    try {
                        if (oos != null) {
                            oos.close();
                        }
                    } catch (Exception e) {
                        Log.e("pushSavedFileName", e.toString());
                    } finally {
                        try {
                            if (os != null) {
                                os.close();
                            }
                        } catch (Exception e) {
                            Log.e("pushSavedFileName", e.toString());
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                // Do nothing
            }
        }.execute();
    }

    protected void modifySpinner(String newItem) {
        spinnerTitleList.add(spinnerTitleList.size(), newItem);
        spinnerArrayAdapter.notifyDataSetChanged();
    }

    protected String savePage(Page page) {
        final String fileName = pageSaveTitle(page);
        /*
        if (savedFileNames.indexOf(fileName) >= 0) {
            // Remove the old one
        }
        */
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                OutputStreamWriter os = null;
                try {
                    /*
                    FileOutputStream os = new FileOutputStream(file);
                    */
                    os = new OutputStreamWriter(mContext.openFileOutput(fileName, Context.MODE_PRIVATE));
                    os.write(params[0]);
                    pushSavedFileName(fileName);
                } catch (Exception e) {
                    Log.e("savePage", e.toString());
                } finally {
                    try {
                        if (os != null) {
                            os.close();
                        }
                    } catch (Exception e) {
                        Log.e("savePage", e.toString());
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) { /* Do nothing */ };
        }.execute(page.getContentText());
        return fileName;
    }

    public void openPage(String pageTitle) {
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                InputStream inputStream = null;
                String content;
                try {
                    inputStream = openFileInput(params[0]);
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String receiveString = "";
                    StringBuilder stringBuilder = new StringBuilder();
                    while ( (receiveString = bufferedReader.readLine()) != null ) {
                        stringBuilder.append(receiveString);
                    }
                    content = stringBuilder.toString();
                    //content = buffer.toString();
                } catch (Exception e) {
                    Log.e("openPage", e.toString());
                    content = "Failed to obtain content.";
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (Exception e) {
                        Log.e("openPage", e.toString());
                    }
                }
                Log.e("openPage", content);
                return content;
            }

            @Override
            protected void onPostExecute(String result) {
                mWebView.loadDataWithBaseURL("", result, "text/html", "UTF-8", "");
            }
        }.execute(pageTitle);
    }

    // Used to determine the name of a file when it is saved
    protected String pageSaveTitle(Page page) {
        String title = page.extractTitle();
        if (title.equals("")) {
            title = page.getPageUrl().toString();
        }
        return title;
    }









}
