package org.chimple.flores.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import static org.chimple.flores.application.P2PApplication.SHARED_PREF;

public class DatabaseInitializer {

    private static final String TAG = DatabaseInitializer.class.getName();

    public static void populateAsync(@NonNull final AppDatabase db, @NonNull final Context context, @NonNull P2PDBApiImpl api) {
        PopulateDbAsync task = new PopulateDbAsync(db, context, api);
        task.execute();
    }

    public static void populateWithTestData(AppDatabase db, Context context) {
        SharedPreferences pref = context.getSharedPreferences(SHARED_PREF, 0);
        String generateUserId = pref.getString("USER_ID", null); // getting String
        Log.i(TAG, "generateUserId :" + generateUserId);
        String generatedDeviceId = pref.getString("DEVICE_ID", null); // getting String
        Log.i(TAG, "generatedDeviceId :" + generatedDeviceId);
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open("database.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        // message contains userId, deviceId, recipientUserId, message, messageType

        String line = "";
        db.beginTransaction();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                String[] columns = line.split(",");

                if (columns.length < 1) {
                    Log.d(TAG + "AppDatabase", "Skipping bad row");
                }

                String userId = generateUserId;
                String deviceId = generatedDeviceId;
                String recipientUserId = columns[2];
                String message = columns[3];
                String messageType = columns[4];

                P2PDBApiImpl.getInstance(context).persistMessage(userId, deviceId, recipientUserId, message, messageType);
            }

            P2PDBApiImpl.getInstance(context).upsertProfile();
            db.setTransactionSuccessful();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    private static class PopulateDbAsync extends AsyncTask<Void, Void, Void> {

        private final AppDatabase mDb;
        private Context context;
        private P2PDBApiImpl api;

        PopulateDbAsync(AppDatabase db, Context context, P2PDBApiImpl api) {
            mDb = db;
            this.context = context;
            this.api = api;
        }


        protected Void doInBackground(final Void... params) {
            populateWithTestData(mDb, this.context);
            return null;
        }

    }


}