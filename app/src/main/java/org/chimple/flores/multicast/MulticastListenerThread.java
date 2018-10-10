package org.chimple.flores.multicast;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;

import static org.chimple.flores.application.P2PApplication.messageEvent;

public class MulticastListenerThread extends MulticastThread {
    private static final String TAG = MulticastListenerThread.class.getSimpleName();

    MulticastListenerThread(Context context, String multicastIP, int multicastPort) {
        super(TAG, context, multicastIP, multicastPort, new Handler());
    }


    public void run() {
        super.run();

        DatagramPacket packet = new DatagramPacket(new byte[512], 512);

        while (running.get()) {
            packet.setData(new byte[1024]);
            Log.d(TAG, "MulticastListenerThread run loop");
            try {
                if (multicastSocket != null)
                    multicastSocket.receive(packet);
                else
                    break;
            } catch (IOException ignored) {
                ignored.printStackTrace();
                continue;
            }

            String data = new String(packet.getData()).trim();

            boolean isLoopBackMessage = getLocalIP().equals(packet.getAddress().getHostAddress()) ? true : false;
            final String consoleMessage = data;
            this.broadcastIncomingMessage(consoleMessage, packet.getAddress().getHostAddress(), isLoopBackMessage);
        }
        if (multicastSocket != null) {
            Log.d(TAG, "MulticastListenerThread -> multicastSocket -> closed");
            this.multicastSocket.close();
        }
    }

    private void broadcastIncomingMessage(String message, String fromIP, boolean isLoopback) {
        if (!isLoopback) {
            Log.d(TAG, "received incoming message:" + message + " from IP:" + fromIP);
            Intent intent = new Intent(messageEvent);
            // You can also include some extra data.
            intent.putExtra("message", message);
            intent.putExtra("fromIP", fromIP);
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
        }
    }
}

