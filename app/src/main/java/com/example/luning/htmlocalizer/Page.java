package com.example.luning.htmlocalizer;

import android.util.Log;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by luning on 2016/03/29.
 */
public class Page {
    private URL pageUrl;
    private String contentText;


    public Page(URL url) {
        pageUrl = url;
    }

    public String getContentText() throws NullPointerException {
        if (contentText == null) {
            throw new NullPointerException("contentText is not set. getContentText");
        }
        return contentText;
    }

    public void setContentText(String content) {
        this.contentText = content;
    }

    public static Boolean isAsset(String strUrl) {
        ArrayList<String> assetExtensions
                = new ArrayList<String>(Arrays.asList(
                ".png", ".jpeg", ".jpg", ".JPG", ".js", ".css"
        ));
        for (String ext: assetExtensions) {
            if (strUrl.indexOf(ext) >= 0) return true;
        }
        return false;
    }

    public static Boolean isPageURL(String strUrl) {
        if (isAsset(strUrl)) {
            return false;
        } else if (strUrl.indexOf('#') >= 0) {
            return false;
        }
        return true;
    }

    public ArrayList<URL> findLinks() throws Exception {
        if (contentText == null) {
            throw new NullPointerException("Page content is not set.");
        }
        Pattern hrefPattern = Pattern.compile("(?i)<a .*?href=\"(.*?)\"");
        Matcher matcher = hrefPattern.matcher(contentText);
        ArrayList<URL> links = new ArrayList<URL>();
        while (matcher.find()) {
            String matchedLink = "";
            try {
                matchedLink = matcher.group(1);
                if (Page.isPageURL(matchedLink)) {
                    URL newUrl = new URL(pageUrl, matchedLink);
                    if (links.indexOf(newUrl) < 0) {
                        links.add(newUrl);
                    }
                }
            } catch (MalformedURLException e) {
                Log.e("findLinks", e.toString());
            } catch (Exception e) {
                throw e;
            }
        }
        //Log.e("findLinks-" + pageUrl.toString(), links.toString());
        return links;
    }

    public URL getPageUrl() { return pageUrl; }

    public String extractDomain() throws NullPointerException {
        if (pageUrl == null) {
            throw new NullPointerException("pageUrl is not set. extractDomain");
        }
        String strUrl = pageUrl.toString();
        String path = pageUrl.getPath();
        int lastSlash = path.lastIndexOf('/');
        return pageUrl.getAuthority() + path.substring(0, lastSlash);
    }

    private String extractInfoFromContent(Pattern pattern) throws NullPointerException {
        if (contentText == null) {
            throw new NullPointerException("contentText is not set. extractTitle");
        }
        Matcher matcher = pattern.matcher(contentText);
        String result;
        try {
            matcher.find();
            result = matcher.group(1);
        } catch (Exception e) {
            result = "";
        }
        return result;
    }

    public String extractTitle() throws NullPointerException {
        Pattern titlePattern = Pattern.compile("(?i)<title>(.*?)</title>");
        return extractInfoFromContent(titlePattern);
    }

    public String extractEncode() throws NullPointerException {
        Pattern encodePattern = Pattern.compile("(?i)<meta .*?charset=\"(.*?)\" | (?i)<meta .*?charset=(.*?)[ >]+ ");
        return extractInfoFromContent(encodePattern);
    }

    public Boolean equals(Page cmpPage) {
        try {
            return cmpPage.getPageUrl().equals(this.pageUrl);
        } catch (NullPointerException e) {
            Log.e("equals", e.toString());
            return false;
        }
    }

    public boolean domainEqual(Page cmpPage) {
        try {
            // Hit is either domain is substring of another
            if (this.extractDomain().indexOf(cmpPage.extractDomain()) >= 0 ||
                    cmpPage.extractDomain().indexOf(this.extractDomain()) >= 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e("domainEqual", e.toString());
            return false;
        }
    }

}







