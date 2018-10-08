package org.chimple.flores.multicast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.chimple.flores.application.NetworkUtil;
import org.chimple.flores.application.P2PApplication;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.chimple.flores.application.P2PApplication.uiMessageEvent;

public class MulticastManager {

    private static final String TAG = MulticastManager.class.getSimpleName();
    private static final String MULTICAST_IP_ADDRESS = "230.0.0.0";
    private static final String MULTICAST_IP_PORT = "4446";
    private Context context;
    private static MulticastManager instance;
    private boolean isListening = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;
    private String multicastIpAddress;
    private int multicastPort;
    private P2PDBApiImpl p2PDBApiImpl;
    private Map<String, HandShakingMessage> handShakingMessages = new HashMap<String, HandShakingMessage>();
    private Map<String, Date> handShakingMessagesReceivedSoFar = new HashMap<String, Date>();

    public static final String multiCastConnectionChangedEvent = "multicast-connection-changed-event";

    private CountDownTimer waitForHandShakingMessagesTimer = null;
    private static final int WAIT_FOR_HAND_SHAKING_MESSAGES = 30 * 1000; // 30 sec
    private static final int HAND_SHAKING_TIME_DIFF = 5 * 60 * 1000;


    public static MulticastManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MulticastManager.class) {
                instance = new MulticastManager(context);
                instance.setMulticastIpAddress(MULTICAST_IP_ADDRESS);
                instance.setMulticastPort(MULTICAST_IP_PORT);
                instance.registerMulticastBroadcasts();
                instance.p2PDBApiImpl = P2PDBApiImpl.getInstance(context);
            }
        }

        return instance;
    }

    private MulticastManager(Context context) {
        this.context = context;
    }

    public void onCleanUp() {
        if (waitForHandShakingMessagesTimer != null) {
            waitForHandShakingMessagesTimer.cancel();
            waitForHandShakingMessagesTimer = null;
        }
        stopListening();
        stopThreads();
        instance.unregisterMulticastBroadcasts();
        instance = null;
    }

    public void startListening() {
        if (!isListening) {
            int status = NetworkUtil.getConnectivityStatusString(this.context);
            if (status != NetworkUtil.NETWORK_STATUS_NOT_CONNECTED) {
                setWifiLockAcquired(true);
                this.multicastListenerThread = new MulticastListenerThread(this.context, getMulticastIP(), getMulticastPort());
                multicastListenerThread.start();
                isListening = true;
            }
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public void stopListening() {
        if (isListening) {
            isListening = false;
            stopThreads();
            setWifiLockAcquired(false);
        }
    }

    public void sendMulticastMessage(String message) {
        if (this.isListening) {
            Log.d(TAG, "sending message: " + message);
            this.multicastSenderThread = new MulticastSenderThread(this.context, getMulticastIP(), getMulticastPort(), message);
            multicastSenderThread.start();
        }
    }

    private void stopThreads() {
        if (this.multicastListenerThread != null)
            this.multicastListenerThread.stopRunning();
        if (this.multicastSenderThread != null)
            this.multicastSenderThread.interrupt();
    }

    private void setWifiLockAcquired(boolean acquired) {
        if (acquired) {
            if (wifiLock != null && wifiLock.isHeld())
                wifiLock.release();

            WifiManager wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                this.wifiLock = wifi.createMulticastLock(TAG);
                wifiLock.acquire();
            }
        } else {
            if (wifiLock != null && wifiLock.isHeld())
                wifiLock.release();
        }
    }

    public void setMulticastIpAddress(String address) {
        this.multicastIpAddress = address;
    }

    public void setMulticastPort(String port) {
        this.multicastPort = Integer.parseInt(port);
    }

    public String getMulticastIP() {
        return this.multicastIpAddress;
    }

    public int getMulticastPort() {
        return this.multicastPort;
    }

    private void unregisterMulticastBroadcasts() {
        if (netWorkChangerReceiver != null) {
            LocalBroadcastManager.getInstance(this.context).unregisterReceiver(netWorkChangerReceiver);
            netWorkChangerReceiver = null;
        }

        if (mMessageReceiver != null) {
            LocalBroadcastManager.getInstance(this.context).unregisterReceiver(mMessageReceiver);
            mMessageReceiver = null;
        }

    }

    private void registerMulticastBroadcasts() {
        LocalBroadcastManager.getInstance(this.context).registerReceiver(netWorkChangerReceiver, new IntentFilter(multiCastConnectionChangedEvent));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(mMessageReceiver, new IntentFilter(P2PApplication.messageEvent));
    }


    private void stopMultiCastOperations() {
        instance.stopListening();
    }

    private void startMultiCastOperations() {
        instance.startListening();

        // after 3 seconds to send initial handshake messages
        instance.sendFindBuddyMessage();
    }


    private BroadcastReceiver netWorkChangerReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            boolean isConnected = intent.getBooleanExtra("isConnected", false);
            if (!isConnected) {
                instance.stopMultiCastOperations();
            } else {
                instance.startMultiCastOperations();
            }
        }
    };


    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            String fromIP = intent.getStringExtra("fromIP");
            boolean isLoopBack = intent.getBooleanExtra("loopback", false);
            if (!isLoopBack) {
                synchronized (MulticastManager.class) {
                    processInComingMessage(message);
                    instance.notifyUI(message, fromIP);
                }
            }
        }
    };


    private void notifyUI(String message, String fromIP) {
        final String consoleMessage = "[" + fromIP + "] " + message + "\n";
        Log.d(TAG, "got message: " + consoleMessage);
        Intent intent = new Intent(uiMessageEvent);
        intent.putExtra("message", consoleMessage);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    private boolean isHandShakingMessage(String message) {
        boolean isHandShakingMessage = false;
        if (message != null) {
            String handShakeMessage = "\"message_type\":\"handshaking\"";
            isHandShakingMessage = message.contains(handShakeMessage);
        }
        return isHandShakingMessage;
    }

    public void sendFindBuddyMessage() {
        instance.sendInitialHandShakingMessage();
    }

    private void sendInitialHandShakingMessage() {
        // construct handshaking message(s)
        // put in queue - TBD
        // send one by one from queue - TBD

        String serializedHandShakingMessage = instance.p2PDBApiImpl.serializeHandShakingMessage();
        Log.d(TAG, "sending initial handshaking message: " + serializedHandShakingMessage);
        instance.sendMulticastMessage(serializedHandShakingMessage);
    }

    private void processInComingMessage(String message) {
        if (instance.isHandShakingMessage(message)) {
            instance.processInComingHandShakingMessage(message);
        }
    }

    private void addNewMessage() {

    }

    private void processInComingHandShakingMessage(String message) {

        Log.d(TAG, "processInComingHandShakingMessage: " + message);
        //parse message and add to all messages
        boolean hadReplyWithSendingHandShakingInformation = instance.parseHandShakingMessage(message);

        // send handshaking information if message received "from" first time
        if (!hadReplyWithSendingHandShakingInformation) {
            Log.d(TAG, "replying back with initial hand shaking message");
            sendInitialHandShakingMessage();
        }

        waitForHandShakingMessagesTimer = new CountDownTimer(WAIT_FOR_HAND_SHAKING_MESSAGES, 1000) {

            public void onTick(long millisUntilFinished) {

            }


            public void onFinish() {
                Log.d(TAG, "waitForHandShakingMessagesTimer finished ... processing sync information ...");
                final Map<String, HandShakingMessage> handShakingMessagesToProcess = Collections.unmodifiableMap(handShakingMessages);
                instance.reset();
                instance.sendSyncInformation(handShakingMessagesToProcess);
            }
        }.start();

    }

    private synchronized void reset() {
        instance.handShakingMessages = null;
        instance.handShakingMessages = new HashMap<String, HandShakingMessage>();
    }

    private void sendSyncInformation(final Map<String, HandShakingMessage> messages) {
        final Set<HandShakingInfo> allHandShakingInfos = new HashSet<HandShakingInfo>();

        Iterator<Map.Entry<String, HandShakingMessage>> entries = messages.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, HandShakingMessage> entry = entries.next();
            Log.i(TAG, "processing message for: " + entry.getKey());
            allHandShakingInfos.addAll(entry.getValue().getInfos());
        }

        List<P2PSyncInfo> allSyncInfos = p2PDBApiImpl.buildSyncInformation(new ArrayList(allHandShakingInfos));
        Iterator<P2PSyncInfo> it = allSyncInfos.iterator();
        while (it.hasNext()) {
            P2PSyncInfo p = it.next();
            String syncMessage = p2PDBApiImpl.convertSingleP2PSyncInfoToJsonUsingStreaming(p);
            instance.sendMulticastMessage(syncMessage);
        }
    }

    private boolean hadRepliedToHandShakingMessage(HandShakingMessage handShakingMessage) {
        boolean alreadyReplied = false;

        if (instance.handShakingMessagesReceivedSoFar.containsKey(handShakingMessage.getFrom())) {
            Date whenLastReceived = (Date) instance.handShakingMessagesReceivedSoFar.get(handShakingMessage.getFrom());
            long diff = System.currentTimeMillis() - HAND_SHAKING_TIME_DIFF;
            if (whenLastReceived.getTime() < diff) {
                Log.d(TAG, "hand shaking message received from " + handShakingMessage.getFrom() + " was 5 min older");
                alreadyReplied = false;
            } else {
                alreadyReplied = true;
            }
        }
        Log.d(TAG, "hadRepliedToHandShakingMessage: " + handShakingMessage.getFrom() + " alreadyReplied:" + alreadyReplied);
        return alreadyReplied;
    }

    private boolean parseHandShakingMessage(String message) {
        boolean repliedToHandShakingMessage = false;
        HandShakingMessage handShakingMessage = p2PDBApiImpl.deSerializeHandShakingInformationFromJson(message);
        if (handShakingMessage != null) {
            repliedToHandShakingMessage = instance.hadRepliedToHandShakingMessage(handShakingMessage);
            Log.d(TAG, "adding handshaking message from user:" + handShakingMessage.getFrom());
            instance.handShakingMessages.put(handShakingMessage.getFrom(), handShakingMessage);
            instance.handShakingMessagesReceivedSoFar.put(handShakingMessage.getFrom(), new Date());
        }
        return repliedToHandShakingMessage;
    }
}
