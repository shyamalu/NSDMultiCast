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
    public static final String NEW_MESSAGE_ADDED = "NEW_MESSAGE_ADDED";
    public static final String REFRESH_DEVICE = "REFRESH_DEVICE";
    public static final String messageEvent = "message-event";
    public static final String uiMessageEvent = "ui-message-event";
    public static final String newMessageAddedOnDevice = "new-message-added-event";
    public static final String refreshDevice = "refresh-device-event";
    public static final String MULTICAST_IP_ADDRESS = "235.1.1.0";
    public static final String MULTICAST_IP_PORT = "4450";

    public static final String CONSOLE_TYPE = "console";
    public static final String LOG_TYPE = "log";
    public static final String CLEAR_CONSOLE_TYPE = "clear-console";


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
        String uuid = UUID.randomUUID().toString();
        editor.putString("USER_ID", uuid);
        editor.putString("DEVICE_ID", uuid+"-device");
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


    public static String getCurrentDevice() {
        SharedPreferences pref = getContext().getSharedPreferences(SHARED_PREF, 0);
        String deviceId = pref.getString("DEVICE_ID", null); // getting String
        return deviceId;
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }


}
