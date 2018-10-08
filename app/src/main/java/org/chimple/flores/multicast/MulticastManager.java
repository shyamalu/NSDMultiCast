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
import org.chimple.flores.db.DBSyncManager;
import org.chimple.flores.db.P2PDBApiImpl;
import org.chimple.flores.db.entity.HandShakingInfo;
import org.chimple.flores.db.entity.HandShakingMessage;
import org.chimple.flores.db.entity.P2PSyncInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.chimple.flores.application.P2PApplication.MULTICAST_IP_ADDRESS;
import static org.chimple.flores.application.P2PApplication.MULTICAST_IP_PORT;
import static org.chimple.flores.application.P2PApplication.NEW_MESSAGE_ADDED;
import static org.chimple.flores.application.P2PApplication.uiMessageEvent;

public class MulticastManager {

    private static final String TAG = MulticastManager.class.getSimpleName();
    private Context context;
    private static MulticastManager instance;
    private boolean isListening = false;
    private MulticastListenerThread multicastListenerThread;
    private MulticastSenderThread multicastSenderThread;
    private WifiManager.MulticastLock wifiLock;
    private String multicastIpAddress;
    private int multicastPort;
    private P2PDBApiImpl p2PDBApiImpl;
    private DBSyncManager dbSyncManager;
    private Map<String, HandShakingMessage> handShakingMessages = new HashMap<String, HandShakingMessage>();
    private Map<String, Date> handShakingMessagesReceivedSoFar = new HashMap<String, Date>();

    public static final String multiCastConnectionChangedEvent = "multicast-connection-changed-event";

    private CountDownTimer waitForHandShakingMessagesTimer = null;
    private static final int WAIT_FOR_HAND_SHAKING_MESSAGES = 2 * 1000; // 2 sec
    private static final int HAND_SHAKING_TIME_DIFF = 1 * 60 * 1000;


    public static MulticastManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MulticastManager.class) {
                instance = new MulticastManager(context);
                instance.setMulticastIpAddress(MULTICAST_IP_ADDRESS);
                instance.setMulticastPort(MULTICAST_IP_PORT);
                instance.registerMulticastBroadcasts();
                instance.dbSyncManager = DBSyncManager.getInstance(context);
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

        if (newMessageAddedReceiver != null) {
            LocalBroadcastManager.getInstance(this.context).unregisterReceiver(newMessageAddedReceiver);
            newMessageAddedReceiver = null;
        }

    }

    private void registerMulticastBroadcasts() {
        LocalBroadcastManager.getInstance(this.context).registerReceiver(netWorkChangerReceiver, new IntentFilter(multiCastConnectionChangedEvent));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(mMessageReceiver, new IntentFilter(P2PApplication.messageEvent));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(newMessageAddedReceiver, new IntentFilter(P2PApplication.newMessageAddedOnDevice));
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

    private BroadcastReceiver newMessageAddedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
//            sendInitialHandShakingMessage(true);
            P2PSyncInfo info = (P2PSyncInfo) intent.getSerializableExtra(NEW_MESSAGE_ADDED);
            if (info != null) {
                String syncMessage = p2PDBApiImpl.convertSingleP2PSyncInfoToJsonUsingStreaming(info);
                instance.sendMulticastMessage(syncMessage);
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
                    processInComingMessage(message, fromIP);
                }
            }
        }
    };


    public void notifyUI(String message, String fromIP) {

        final String consoleMessage = "[" + fromIP + "]: " + message + "\n";
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

    private boolean isSyncInfoMessage(String message) {
        boolean isSyncInfoMessage = false;
        if (message != null) {
            String syncInfoMessage = "\"message_type\":\"syncInfoMessage\"";
            isSyncInfoMessage = message.contains(syncInfoMessage);
        }
        return isSyncInfoMessage;
    }


    private void sendInitialHandShakingMessage(boolean needAcknowlegement) {
        // construct handshaking message(s)
        // put in queue - TBD
        // send one by one from queue - TBD
        String serializedHandShakingMessage = instance.p2PDBApiImpl.serializeHandShakingMessage(needAcknowlegement);
        Log.d(TAG, "sending initial handshaking message: " + serializedHandShakingMessage);
        instance.sendMulticastMessage(serializedHandShakingMessage);
    }

    public void processInComingMessage(String message, String fromIP) {
        if (instance.isHandShakingMessage(message)) {
            instance.processInComingHandShakingMessage(message);
        } else if (instance.isSyncInfoMessage(message)) {
            String result = instance.processInComingSyncInfoMessage(message);
            instance.notifyUI(result, fromIP);
        }
    }

    public void addNewMessage(String message) {
        dbSyncManager.addMessage(P2PApplication.getLoggedInUser(), null, "Chat", message);
    }

    private void processInComingHandShakingMessage(String message) {

        Log.d(TAG, "processInComingHandShakingMessage: " + message);
        //parse message and add to all messages
        boolean hadReplyWithSendingHandShakingInformation = instance.parseHandShakingMessage(message);

        // send handshaking information if message received "from" first time
        if (!hadReplyWithSendingHandShakingInformation) {
            Log.d(TAG, "replying back with initial hand shaking message");
            sendInitialHandShakingMessage(false);
        }

        waitForHandShakingMessagesTimer = new CountDownTimer(WAIT_FOR_HAND_SHAKING_MESSAGES, 1000) {

            public void onTick(long millisUntilFinished) {

            }


            public void onFinish() {
                Log.d(TAG, "waitForHandShakingMessagesTimer finished ... processing sync information ...");
                List<String> computedSyncMessages = instance.computeSyncInformation();
                instance.sendSyncMessages(computedSyncMessages);
            }
        }.start();

    }

    private String processInComingSyncInfoMessage(String message) {
        return P2PDBApiImpl.getInstance(instance.context).persistP2PSyncInfos(message);
    }

    private void reset() {
        instance.handShakingMessages = null;
        instance.handShakingMessages = new HashMap<String, HandShakingMessage>();
    }

    public List<String> computeSyncInformation() {
        List<String> computedMessages = new ArrayList<String>();

        final Map<String, HandShakingMessage> messages = Collections.unmodifiableMap(handShakingMessages);
        instance.reset();

        final Set<HandShakingInfo> allHandShakingInfos = new TreeSet<HandShakingInfo>(new Comparator<HandShakingInfo>() {
            @Override
            public int compare(HandShakingInfo o1, HandShakingInfo o2) {
                if (o1.getDeviceId().equalsIgnoreCase(o2.getDeviceId())) {
                    if (o1.getSequence().longValue() > o2.getSequence().longValue()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
                return 1;
            }
        });

        Iterator<Map.Entry<String, HandShakingMessage>> entries = messages.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, HandShakingMessage> entry = entries.next();
            Log.i(TAG, "processing message for: " + entry.getKey());
            allHandShakingInfos.addAll(entry.getValue().getInfos());
        }

        Iterator<HandShakingInfo> itR = allHandShakingInfos.iterator();
        long minSequenceForSameDeviceId = -1;
        while (itR.hasNext()) {
            HandShakingInfo info = itR.next();
            if (info.getDeviceId().equalsIgnoreCase(P2PApplication.getCurrentDevice())) {
                long curSequence = info.getSequence().longValue();
                if (minSequenceForSameDeviceId == -1) {
                    minSequenceForSameDeviceId = curSequence;
                }

                if (curSequence > minSequenceForSameDeviceId) {
                    itR.remove();
                }

            }
        }

        List<P2PSyncInfo> allSyncInfos = p2PDBApiImpl.buildSyncInformation(new ArrayList(allHandShakingInfos));
        Iterator<P2PSyncInfo> it = allSyncInfos.iterator();
        while (it.hasNext()) {
            P2PSyncInfo p = it.next();
            String syncMessage = p2PDBApiImpl.convertSingleP2PSyncInfoToJsonUsingStreaming(p);
            computedMessages.add(syncMessage);
        }
        return computedMessages;
    }

    private void sendSyncMessages(List<String> computedMessages) {
        Iterator<String> it = computedMessages.iterator();
        while (it.hasNext()) {
            String p = it.next();
            instance.sendMulticastMessage(p);
        }
    }

    private boolean hadRepliedToHandShakingMessage(HandShakingMessage handShakingMessage) {
        boolean alreadyReplied = false;

        if (instance.handShakingMessagesReceivedSoFar.containsKey(handShakingMessage.getFrom())) {
            Date whenLastReceived = (Date) instance.handShakingMessagesReceivedSoFar.get(handShakingMessage.getFrom());
            boolean needReply = handShakingMessage.getReply().equalsIgnoreCase("true");
            long diff = System.currentTimeMillis() - HAND_SHAKING_TIME_DIFF;
            //if (whenLastReceived.getTime() < diff || needReply) {
            if (needReply) {
                Log.d(TAG, "hand shaking message received from " + handShakingMessage.getFrom() + " with need to reply");
                alreadyReplied = false;
            } else {
                alreadyReplied = true;
            }
        }
        Log.d(TAG, "hadRepliedToHandShakingMessage: " + handShakingMessage.getFrom() + " alreadyReplied:" + alreadyReplied);
        return alreadyReplied;
    }

    public boolean parseHandShakingMessage(String message) {
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

    public void sendFindBuddyMessage() {
        instance.sendInitialHandShakingMessage(true);
    }

}
