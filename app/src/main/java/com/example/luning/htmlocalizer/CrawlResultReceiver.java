package com.example.luning.htmlocalizer;

import android.database.CursorJoiner;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 * Created by luning on 2016/04/08.
 */
public class CrawlResultReceiver extends ResultReceiver{
    private Receiver receiver;

    // Constructor takes a handler
    public CrawlResultReceiver(Handler handler) {
        super(handler);
    }

    // Setter for assigning the receiver
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    // Defines our event interface for communication
    public interface Receiver {
        public void onReceiveResult(int resultCode, Bundle resultData);
    }

    // Delegate method which passes the result to the receiver if the receiver has been assigned
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (receiver != null) {
            receiver.onReceiveResult(resultCode, resultData);
        }
    }
}
