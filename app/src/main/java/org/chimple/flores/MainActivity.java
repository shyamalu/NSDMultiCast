package org.chimple.flores;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.support.v7.widget.Toolbar;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chimple.flores.application.P2PApplication;
import org.chimple.flores.multicast.MulticastManager;

public class MainActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private EditText multicastIPField;
    private EditText multicastPortField;
    private TextView consoleView;
    private EditText messageToSendField;
    private MulticastManager manager;
    private MainActivity that = this;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setLogo(R.mipmap.ic_launcher);
        manager = P2PApplication.multicastManager;
    }


    protected void onStart() {
        super.onStart();
        this.multicastIPField = (EditText) findViewById(R.id.multicastIP);
        this.multicastPortField = (EditText) findViewById(R.id.multicastPort);
        this.consoleView = (TextView) findViewById(R.id.consoleTextView);
        this.messageToSendField = (EditText) findViewById(R.id.messageToSend);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(P2PApplication.uiMessageEvent));
    }


    protected void onStop() {
        super.onStop();
    }


    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        manager.onCleanUp();
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void onToggleHexCheckbox(View view) {
        if(view.getId() == R.id.hexDisplayCheckBox) {

        }
    }

    public void onButton(View view) {
        // Hide the keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

        if (view.getId() == R.id.startListeningButton) {
            if (manager.isListening()) {
                manager.stopListening();
            } else {
                manager.startListening();
            }
        } else if (view.getId() == R.id.clearConsoleButton) {
            clearConsole();
        } else if (view.getId() == R.id.sendMessageButton) {
            sendMulticastMessage(getMessageToSend());
        }
    }

    private void clearConsole() {
        this.consoleView.setText("");
        this.consoleView.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    public String getMessageToSend() {
        return this.messageToSendField.getText().toString();
    }

    private void sendMulticastMessage(String message) {
        if (this.multicastIPField.getText().toString() != null && this.multicastIPField.getText().toString().length() > 0) {
            manager.setMulticastIpAddress(this.multicastIPField.getText().toString());
        }

        if (this.multicastPortField.getText().toString() != null && this.multicastPortField.getText().toString().length() > 0) {
            manager.setMulticastPort(this.multicastPortField.getText().toString());
        }

        manager.sendMulticastMessage(message);
    }

    public void outputTextToConsole(String message) {

        this.consoleView.append(message);

        ScrollView scrollView = ((ScrollView) this.consoleView.getParent());
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            that.outputTextToConsole(message);
        }
    };

}
