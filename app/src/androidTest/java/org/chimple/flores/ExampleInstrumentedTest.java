package org.chimple.flores;


import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.chimple.flores.application.P2PApplication;
import org.chimple.flores.db.AppDatabase;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.dao.P2PSyncInfoDao;
import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;
import org.chimple.flores.db.entity.P2PUserIdMessage;
import org.chimple.flores.multicast.MulticastManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import kotlin.jvm.JvmField;

import static org.chimple.flores.application.P2PApplication.SHARED_PREF;
import static org.junit.Assert.assertEquals;


@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = ExampleInstrumentedTest.class.getName();
    private AppDatabase database;
    private P2PSyncInfoDao p2PSyncInfoDao;
    private P2PDBApiImpl p2pDBAPI = null;
    private MulticastManager manager;
    private Context context;
    private List<String> users = new ArrayList<String>();

    @Rule
    @JvmField
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
        try {
            database = Room.inMemoryDatabaseBuilder(context.getApplicationContext(), AppDatabase.class)
                    .allowMainThreadQueries()
                    .build();

        } catch (Exception ex) {
            Log.i("test", ex.getMessage());
        }

        p2PSyncInfoDao = database.p2pSyncDao();
        p2pDBAPI = P2PDBApiImpl.getInstance(context.getApplicationContext());
        manager = MulticastManager.getInstance(context.getApplicationContext());
        setUpTestUsers();
    }

    private void seedInitialDataForA() {
        for (int i = 0; i < 10; i++) {
            setUpTestData("A", i + 1);
        }

        for (int i = 0; i < 5; i++) {
            setUpTestData("B", i + 1);
        }

        for (int i = 0; i < 6; i++) {
            setUpTestData("C", i + 1);
        }
    }

    private void seedInitialDataForB() {
        for (int i = 0; i < 5; i++) {
            setUpTestData("A", i + 1);
        }

        for (int i = 0; i < 10; i++) {
            setUpTestData("B", i + 1);
        }

        for (int i = 0; i < 4; i++) {
            setUpTestData("C", i + 1);
        }
    }

    private void seedInitialDataForC() {
        for (int i = 0; i < 3; i++) {
            setUpTestData("B", i + 1);
        }

        for (int i = 0; i < 10; i++) {
            setUpTestData("C", i + 1);
        }
    }

    private void seedInitialDataForD() {
        for (int i = 0; i < 10; i++) {
            setUpTestData("D", i + 1);
        }
        for (int i = 0; i < 5; i++) {
            int s = i + 1;
            setUpTestData("A", s);
            manager.getAllSyncInfosReceived().add("A-device"+"_"+"A"+"_"+s);
        }

    }


    private void setUpTestUsers() {
        users.add("A");
        users.add("B");
        users.add("C");
        users.add("D");
    }


    private void setUpTestData(String user, long sequence) {
        String userId1 = user;
        String deviceId = user + "-device";
        String recepientUserId = "recepientUserId";
        String message = generateMessage(user);
        String messageType = "Chat";
        p2pDBAPI.addMessage(userId1, deviceId, sequence, recepientUserId, messageType, message);
    }

    private String generateMessage(String from) {
        byte[] array = new byte[7]; // length is bounded by 7
        new Random().nextBytes(array);
        String generatedString = new String(array, Charset.forName("UTF-8"));
        return "Hello from: " + from + " with - " + generatedString;
    }

    private void cleanData() {
        p2pDBAPI.deleteDataPerDeviceId("A-device");
        p2pDBAPI.deleteDataPerDeviceId("B-device");
        p2pDBAPI.deleteDataPerDeviceId("C-device");
        p2pDBAPI.deleteDataPerDeviceId("D-device");
    }

    private void updateInfos(String userId, String deviceId) {
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", userId);
        editor.putString("DEVICE_ID", deviceId);
        editor.commit(); // commit changes
    }

    @Test
    public void testNewLogic() {
        cleanData();
        updateInfos("D", "D-device");
        seedInitialDataForD();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        String handShakingFromA = "{\"from\":\"A-device\",\"infos\":[{\"deviceId\":\"A-device\",\"sequence\":10,\"userId\":\"A\"},{\"deviceId\":\"B-device\",\"sequence\":5,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":6,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        String handShakingFromB = "{\"from\":\"B-device\",\"infos\":[{\"deviceId\":\"A-device\",\"sequence\":5,\"userId\":\"A\"},{\"deviceId\":\"B-device\",\"sequence\":10,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":4,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
//        String handShakingFromC = "{\"from\":\"C-device\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
//        manager.parseHandShakingMessage(handShakingFromC);
        manager.parseHandShakingMessage(handShakingFromA);
        manager.parseHandShakingMessage(handShakingFromB);
        List<String> jsons = manager.generateSyncInfoPullRequest(manager.getAllHandShakeMessagesInCurrentLoop());


        List<String> requests = new ArrayList<String>();
        for (String json : jsons) {
            if (json.contains("\"md\":\"B-device\"")) {
                cleanData();
                seedInitialDataForB();
                updateInfos("B", "B-device");
            }
            else if (json.contains("\"md\":\"A-device\"")) {
                cleanData();
                seedInitialDataForA();
                updateInfos("A", "A-device");
            }
            requests.addAll(manager.processInComingSyncRequestMessage(json));
            cleanData();
        }

        updateInfos("D", "D-device");
        seedInitialDataForD();
        List<P2PSyncInfo> a = p2pDBAPI.getSyncInformationByUserIdAndDeviceId("A", "A-device");
        List<P2PSyncInfo> d = p2pDBAPI.getSyncInformationByUserIdAndDeviceId("D", "D-device");

        for (String s: requests) {
            manager.processInComingSyncInfoMessage(s, "");
        }


        List<P2PSyncInfo> a1 = p2pDBAPI.getSyncInformationByUserIdAndDeviceId("A", "A-device");
        List<P2PSyncInfo> b1 = p2pDBAPI.getSyncInformationByUserIdAndDeviceId("B", "B-device");
        List<P2PSyncInfo> d1 = p2pDBAPI.getSyncInformationByUserIdAndDeviceId("D", "D-device");
//        assertEquals(computedMessages.size(), 12);
//        Iterator<String> it = computedMessages.iterator();
//        while (it.hasNext()) {
//            String p = it.next();
//            Log.d(TAG, p);
//        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("D");
        assertEquals(p2PSyncInfos.size(), 0);
    }

    @Test
    public void testHandShakingMessagesWithUserAWithHandShakeFromC() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "A");
        editor.commit(); // commit changes
        seedInitialDataForA();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        String handShakingFromC = "{\"from\":\"C\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromC);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 12);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("A");
        assertEquals(p2PSyncInfos.size(), 0);
    }

    @Test
    public void testHandShakingMessagesWithUserAWithHandShakeFromD() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "A");
        editor.commit(); // commit changes
        seedInitialDataForA();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        String handShakingFromD = "{\"from\":\"D\",\"infos\":[{\"deviceId\":\"D-device\",\"sequence\":10,\"userId\":\"D\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromD);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 21);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("A");
        assertEquals(p2PSyncInfos.size(), 0);
    }

    @Test
    public void testHandShakingMessagesWithUserAWithHandShakeFromBAndC() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "A");
        editor.commit(); // commit changes
        seedInitialDataForA();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);

        String handShakingFromB = "{\"from\":\"B\",\"infos\":[{\"deviceId\":\"A-device\",\"sequence\":5,\"userId\":\"A\"},{\"deviceId\":\"B-device\",\"sequence\":10,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":4,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        String handShakingFromC = "{\"from\":\"C\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromB);
        manager.parseHandShakingMessage(handShakingFromC);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 5);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
    }


    @Test
    public void testHandShakingMessagesWithUserBWithHandShakeFromAAndC() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "B");
        editor.putString("DEVICE_ID", "B-device");
        editor.commit(); // commit changes
        seedInitialDataForB();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        // {"from":"B","infos":[{"deviceId":"A-device","sequence":5,"userId":"A"},{"deviceId":"B-device","sequence":10,"userId":"B"},{"deviceId":"C-device","sequence":4,"userId":"C"}],"message_type":"handshaking"}
        String handShakingFromA = "{\"from\":\"A\",\"infos\":[{\"deviceId\":\"A-device\",\"sequence\":10,\"userId\":\"A\"},{\"deviceId\":\"B-device\",\"sequence\":5,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":6,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        String handShakingFromC = "{\"from\":\"C\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromA);
        manager.parseHandShakingMessage(handShakingFromC);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 7);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("B");
        assertEquals(p2PSyncInfos.size(), 0);
    }


    @Test
    public void testHandShakingMessagesWithUserBWithHandShakeFromA() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "B");
        editor.putString("DEVICE_ID", "B-device");
        editor.commit(); // commit changes
        seedInitialDataForB();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        // {"from":"B","infos":[{"deviceId":"A-device","sequence":5,"userId":"A"},{"deviceId":"B-device","sequence":10,"userId":"B"},{"deviceId":"C-device","sequence":4,"userId":"C"}],"message_type":"handshaking"}
        String handShakingFromA = "{\"from\":\"A\",\"infos\":[{\"deviceId\":\"A-device\",\"sequence\":10,\"userId\":\"A\"},{\"deviceId\":\"B-device\",\"sequence\":5,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":6,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
//        String handShakingFromC = "{\"from\":\"C\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromA);
//        manager.parseHandShakingMessage(handShakingFromC);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 5);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("B");
        assertEquals(p2PSyncInfos.size(), 0);
    }

    @Test
    public void testHandShakingMessagesWithUserBWithHandShakeFromC() {
        cleanData();
        SharedPreferences pref = P2PApplication.getContext().getSharedPreferences(SHARED_PREF, 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("USER_ID", "B");
        editor.putString("DEVICE_ID", "B-device");
        editor.commit(); // commit changes
        seedInitialDataForB();
        String serializedHandShakingMessage = p2pDBAPI.serializeHandShakingMessage(false);
        Log.d(TAG, serializedHandShakingMessage);
        // {"from":"B","infos":[{"deviceId":"A-device","sequence":5,"userId":"A"},{"deviceId":"B-device","sequence":10,"userId":"B"},{"deviceId":"C-device","sequence":4,"userId":"C"}],"message_type":"handshaking"}
        String handShakingFromC = "{\"from\":\"C\",\"infos\":[{\"deviceId\":\"B-device\",\"sequence\":3,\"userId\":\"B\"},{\"deviceId\":\"C-device\",\"sequence\":10,\"userId\":\"C\"}],\"message_type\":\"handshaking\"}";
        manager.parseHandShakingMessage(handShakingFromC);
        List<String> computedMessages = manager.computeSyncInformation();
        assertEquals(computedMessages.size(), 12);
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            Log.d(TAG, p);
        }
        cleanData();
        List<P2PSyncInfo> p2PSyncInfos = p2pDBAPI.getInfoByUserId("B");
        assertEquals(p2PSyncInfos.size(), 0);
    }

    @Test
    public void testSyncInfoMessageProcess() {
        String message = "{\"infos\":[{\"deviceId\":\"A-device\",\"id\":1153,\"loggedAt\":\"Oct 7, 2018 4:15:05 PM\",\"message\":\"Chat\",\"messageType\":\"Hello from: A with - �\\u0007���p:\",\"recipientUserId\":\"recepientUserId\",\"sequence\":7,\"userId\":\"A\"}],\"message_type\":\"syncInfoMessage\"}";
        manager.processInComingMessage(message, "");
    }

    @After
    public void tearDown() {
        database.clearAllTables();
        database.close();
    }
}
