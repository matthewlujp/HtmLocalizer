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

    public static Boolean isAsset(String url) {
        ArrayList<String> assetExtensions
                = new ArrayList<String>(Arrays.asList(
                ".png", ".jpeg", ".jpg", ".JPG", ".js", ".css"
        ));
        for (String ext: assetExtensions) {
            if (url.contains(ext)) return true;
        }
        return false;
    }

    public static boolean isPageURL(String url) {
        if (isAsset(url)) {
            return false;
        } else if (url.contains("#")) {
            return false;
        }
        return true;
    }

    public static boolean isJsURL(String url) {
        return url.contains(".js");
    }

    public static boolean isCSSURL(String url) {
        return url.contains(".css");
    }

    public static boolean isImageURL(String url) {
        if (url.contains(".jpeg") || url.contains(".jpg") || url.contains(".JPG")) return true;
        else if (url.contains(".png")) return true;
        else return false;
    }

    public ArrayList<URL> findLinks() throws Exception {
        if (contentText == null) {
            throw new NullPointerException("Page content is not set.");
        }
        String linkPattern = "((?i)<a .*?href|<link .*?href|<script .*?src|<img .*?src)=\"(.*?)\"";
        Pattern hrefPattern = Pattern.compile(linkPattern);
        Matcher matcher = hrefPattern.matcher(contentText);
        ArrayList<URL> links = new ArrayList<URL>();
        while (matcher.find()) {
            String matchedLink = "";
            try {
                matchedLink = matcher.group(2);
                if (Page.isPageURL(matchedLink) || isJsURL(matchedLink) || isCSSURL(matchedLink) || isImageURL(matchedLink)) {
                    URL newUrl = new URL(pageUrl, matchedLink);
                    if (!links.contains(newUrl)) {
                        links.add(newUrl);
                    }
                }
            } catch (MalformedURLException e) {
                // Log.e("findLinks", matchedLink + " - " + e.toString());
            } catch (Exception e) {
                throw e;
            }
        }
        //Log.e("findLinks-" + pageUrl.toString(), links.toString());
        return links;
    }

    public URL getPageUrl() { return pageUrl; }

    public String extractDirectory() throws NullPointerException {
        if (pageUrl == null) {
            throw new NullPointerException("pageUrl is not set. extractDomain");
        }
        String strUrl = pageUrl.toString();
        String path = pageUrl.getPath();
        int lastSlash = path.lastIndexOf('/');
        return pageUrl.getAuthority() + path.substring(0, lastSlash + 1);
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
            // Log.e("equals", e.toString());
            return false;
        }
    }

    public boolean domainEqual(Page orgPage) {
        try {
            // Hit is either domain is substring of another
            if (extractDirectory().contains(orgPage.extractDirectory())) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            // Log.e("domainEqual", e.toString());
            return false;
        }
    }

}







