package com.example.luning.htmlocalizer;

import android.graphics.Bitmap;

/**
 * Created by luning on 2016/04/09.
 */
public class SiteListItem {
    private String mTitle, mURL;
    private Bitmap mIcon;

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public String getURL() {
        return mURL;
    }

    public void setURL(String mURL) {
        this.mURL = mURL;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void setIcon(Bitmap mIcon) {
        this.mIcon = mIcon;
    }

    public SiteListItem(String title, String url) {
        mTitle = title;
        mURL = url;
        
    }
    
    @Override
    public String toString() {
        return mTitle;
    }
}
