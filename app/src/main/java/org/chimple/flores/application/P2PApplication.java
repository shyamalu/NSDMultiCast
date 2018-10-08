package org.chimple.flores.application;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.multicast.MulticastManager;

import java.util.UUID;


public class P2PApplication extends Application {
    public static final String SHARED_PREF = "shardPref";
    public static final String USER_ID = "USER_ID";
    public static final String DEVICE_ID = "DEVICE_ID";
    public static final String messageEvent = "message-event";
    public static final String uiMessageEvent = "ui-message-event";

    private static final String TAG = P2PApplication.class.getName();
    private static Context context;
    private P2PApplication that;
    public static AppDatabase db;
    public static MulticastManager multicastManager;


    public void onCreate() {
        super.onCreate();
        initialize();
        context = this;
        that = this;
    }


    private void initialize() {
        Log.d(TAG, "Initializing...");

        Thread initializationThread = new Thread() {

            public void run() {
                P2PContext.getInstance().initialize(P2PApplication.this);
                P2PApplication.this.createShardProfilePreferences();
                db = AppDatabase.getInstance(P2PApplication.this);
                multicastManager = MulticastManager.getInstance(P2PApplication.this);
                Log.i(TAG, "app database instance" + String.valueOf(db));

                initializationComplete();
            }
        };

        initializationThread.start();
    }

    private void createShardProfilePreferences() {
        SharedPreferences pref = this.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", UUID.randomUUID().toString());
        editor.putString("DEVICE_ID", UUID.randomUUID().toString());
        editor.commit(); // commit changes
    }


    private void initializationComplete() {
        Log.i(TAG, "Initialization complete...");
    }

    public static Context getContext() {
        return context;
    }

    public static String getLoggedInUser() {
        SharedPreferences pref = getContext().getSharedPreferences(SHARED_PREF, 0);
        String userId = pref.getString("USER_ID", null); // getting String
        return userId;
    }


    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }


}
