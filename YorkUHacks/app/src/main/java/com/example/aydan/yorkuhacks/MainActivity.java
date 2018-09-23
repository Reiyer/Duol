package com.example.aydan.yorkuhacks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MotionEvent;
import android.content.Intent;
import android.util.Log;


import android.Manifest;



import android.widget.Toast;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;




import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status;
import com.google.android.gms.nearby.connection.Strategy;


public class MainActivity extends Activity{
    public Boolean attacking;
    public int result = 0;
    public String direction;
    public String oppdir;
    /*
    wifi bullshit starts here

     */
    private final String codeName = "testuser";
    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private static final Strategy STRATEGY = Strategy.P2P_STAR;


    private ConnectionsClient connectionsClient;
    private int myScore;
    private String opponentEndpointId;
    private String opponentName;
    private int opponentScore;

    private TextView opponentText;
    private TextView statusText;
    private TextView scoreText;
    private Button findOpponentButton;
    private Button disconnectButton;


    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    Log.d("MainActivity", "got payload");
                    oppdir = new String(payload.asBytes(), UTF_8);
                    if(oppdir == "UP"){
                        myScore++;
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == Status.SUCCESS && direction != null && oppdir != null) {
                        finishRound();
                    }
                }
            };
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i("MainActivity", "onEndpointFound: endpoint found, connecting");
                    connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {}
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i("MainActivity", "onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                    opponentName = connectionInfo.getEndpointName();
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i("MainActivity", "onConnectionResult: connection successful");

                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();

                        opponentEndpointId = endpointId;
                        setOpponentName(opponentName);
                        setStatusText("status_connected");
                        setButtonState(true);
                    } else {
                        Log.i("MainActivity", "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i("MainActivity", "onDisconnected: disconnected from the opponent");
                    resetGame();
                }
            };

    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    @Override
    protected void onStop() {
        connectionsClient.stopAllEndpoints();
        resetGame();

        super.onStop();
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "error missing permissions", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        recreate();
    }

    /** Finds an opponent to play the game with using Nearby Connections. */
    public void findOpponent(View view) {
        startAdvertising();
        startDiscovery();
        setStatusText("Searching...");
        findOpponentButton.setEnabled(false);
    }

    /** Disconnects from the opponent and reset the UI. */
    public void disconnect(View view) {
        connectionsClient.disconnectFromEndpoint(opponentEndpointId);
        resetGame();
    }


    /** Starts looking for other players using Nearby Connections. */
    private void startDiscovery() {
        attacking = false;
        Toast toast = Toast.makeText(getApplicationContext(), "You are defending", Toast.LENGTH_LONG);
        toast.show();

        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startDiscovery(
                getPackageName(), endpointDiscoveryCallback, new DiscoveryOptions(STRATEGY));
    }

    private void startAdvertising() {
        attacking = true;
        Toast toast = Toast.makeText(getApplicationContext(), "You attac", Toast.LENGTH_LONG);
        toast.show();
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
                codeName, getPackageName(), connectionLifecycleCallback, new AdvertisingOptions(STRATEGY));
    }
    private void resetGame() {
        opponentEndpointId = null;
        opponentName = null;
        oppdir = null;
        opponentScore = 0;
        direction = null;
        myScore = 0;

        setOpponentName("no_opponent");
        setStatusText("status_disconnected");
        updateScore(myScore, opponentScore);
        setButtonState(false);
    }

    private void sendGameChoice() {

        connectionsClient.sendPayload(
                opponentEndpointId, Payload.fromBytes(direction.getBytes(UTF_8)));

        setStatusText("placeholder");

        Log.d("MainActivity", "sent payload");
        // No changing your mind!
        //setGameChoicesEnabled(false);
    }

    public void makeMove() {

        sendGameChoice();

    }

    private void finishRound() {
        if (direction.equals(oppdir) && attacking == false) {
            // Loss!
            Toast toast = Toast.makeText(getApplicationContext(), "You were hit", Toast.LENGTH_LONG);
            toast.show();
            opponentScore++;


        } else if(direction.equals(oppdir) && attacking == true) {
            Toast toast = Toast.makeText(getApplicationContext(), "Your attack was parried", Toast.LENGTH_LONG);
            toast.show();
            attacking = false;
            // Loss


        }

        else if(direction.equals(oppdir) && attacking == false) {
            Toast toast = Toast.makeText(getApplicationContext(), "You parried an attack", Toast.LENGTH_LONG);
            toast.show();
            attacking = true;



        }

        else{
            Toast toast = Toast.makeText(getApplicationContext(), "Your landed an attack", Toast.LENGTH_LONG);
            toast.show();
            myScore++;



        }
        direction = null;
        oppdir = null;

        updateScore(myScore, opponentScore);

        // Ready for another round

    }

    private void setButtonState(boolean connected) {
        findOpponentButton.setEnabled(true);
        findOpponentButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);

        //setGameChoicesEnabled(connected);
    }

    private void setStatusText(String text) {
        statusText.setText(text);
    }

    private void setOpponentName(String opponentName) {
        opponentText.setText(getString(R.string.opponent_name, opponentName));
    }

    private void updateScore(int myScore, int opponentScore) {
        scoreText.setText(getString(R.string.game_score, myScore, opponentScore));
    }

/*
WIFI BULLSHIT ENDS HERE

 */

    public Boolean sensorToggled = false;

    public static int START_WINDOW = 1000;
    public static int TIMING_WINDOW = 3000;
    public static final Random rand = new Random();

    public Timer timer = new Timer();

    public TimerTask timerTaskLoad;
    public TimerTask timerTaskEnd;

    public int playState = -1;

    public Intent sensorIntent;

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);

        findOpponentButton = findViewById(R.id.find_opponent);
        disconnectButton = findViewById(R.id.disconnect);

        opponentText = findViewById(R.id.opponent_name);
        statusText = findViewById(R.id.status);
        scoreText = findViewById(R.id.score);

        TextView nameView = findViewById(R.id.name);
        nameView.setText(getString(R.string.codename, codeName));

        connectionsClient = Nearby.getConnectionsClient(this);

        resetGame();

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("result"));

        sensorToggled = true;
        sensorIntent = new Intent(MainActivity.this, SensorActivity.class);
        startService(sensorIntent);

        //createGesture();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int eventaction = event.getAction();


        if(!sensorToggled){
            connectionsClient.sendPayload(
                    opponentEndpointId, Payload.fromBytes(direction.getBytes(UTF_8)));
            sensorToggled = true;
        }
        return true;

    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            int result = intent.getIntExtra("result", 1);
            //Log.d("receiver", "Got message: " + Integer.toString(result));
            if(sensorToggled) {
                switch (result) {
                    case 1:
                        Log.d("MainActivity", "LEFT");
                        direction = "LEFT";
                        break;
                    case 2:
                        Log.d("MainActivity", "UP");
                        direction = "UP";
                        break;
                    case 3:
                        Log.d("MainActivity", "RIGHT");
                        direction = "RIGHT";
                        break;
                    case 4:
                        Log.d("MainActivity", "DOWN");
                        direction = "DOWN";
                        break;
                    case 0:
                        //fail to swipe in time
                        Log.d("MainActivity", "MISS!");
                        direction = "MISS!";
                        break;
                }
                if(playState == result){
                    Log.d("MainActivity", "HIT!");
                }else{
                    Log.d("MainActivity", "WRONG MOTION!");
                }
                sensorToggled = false;

                //createGesture();
            }
        }
    };

    public void createGesture(){
        playState = -1;
        timer.cancel();
        timer = new Timer();

        timerTaskLoad = new TimerTask(){
            @Override
            public void run() {
                playState = rand.nextInt(4)+1;

                switch(playState) {
                    case 1:
                        Log.d("MainActivity", "SWIPE LEFT");
                        break;
                    case 2:
                        Log.d("MainActivity", "SWIPE UP");
                        break;
                    case 3:
                        Log.d("MainActivity", "SWIPE RIGHT");
                        break;
                    case 4:
                        Log.d("MainActivity", "SWIPE DOWN");
                        break;
                    default:
                        Log.d("MainActivity", "HOW DOES THIS EVEN HAPPEN");
                        break;
                }
            }
        };

        timerTaskEnd = new TimerTask(){
            @Override
            public void run(){
                playState = -1;
                Log.d("MainActivity", "MISS!");
                sensorToggled = false;
                createGesture();
            }

        };

        timer.schedule(timerTaskLoad, START_WINDOW);
        timer.schedule(timerTaskEnd, TIMING_WINDOW);

    }

    //commited at 12:20 by Aydan and Billiam



}