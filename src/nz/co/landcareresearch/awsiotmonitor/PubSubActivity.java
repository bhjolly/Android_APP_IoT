/**

 */

package nz.co.landcareresearch.awsiotmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.support.v4.app.NotificationCompat;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.Context;
import android.graphics.Color;
import android.annotation.SuppressLint;

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;

import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;

// class with 3 public static variables to set AWS 'private static final' vars below
import nz.co.landcareresearch.awsiotmonitor.AWSConnStrings;

public class PubSubActivity extends Activity {

    static final String LOG_TAG = PubSubActivity.class.getCanonicalName();

    // --- Constants to modify per your configuration ---

    // Customer specific IoT endpoint
    // e.g. XXXXXXXXXX.iot.<region>.amazonaws.com,
    private static final String CUSTOMER_SPECIFIC_IOT_ENDPOINT = AWSConnStrings.CUSTOMER_SPECIFIC_IOT_ENDPOINT;

    // Cognito Pool ID
    // e.g. COGNITO_POOL_ID = "<region>:xxxxxxxx-xxxx-xxxxa-xxxx-xxxxxxxxxxxx";
    private static final String COGNITO_POOL_ID = AWSConnStrings.COGNITO_POOL_ID;

    // Region of AWS IOT
    // e.g. MY_REGION = Regions.AP_SOUTHEAST_2;
    private static final Regions MY_REGION = AWSConnStrings.MY_REGION;

    private static String name_filter = "*";

    TextView tvName;
    TextView tvDateTime;
    TextView tvClientId;
    TextView tvStatus;
    TextView tvData;

    Button btnConnect;
    Button btnDisconnect;
    Button btnFilter;

    EditText etTopic;
    EditText etName;

    AWSIotMqttManager mqttManager;
    String clientId;

    CognitoCachingCredentialsProvider credentialsProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvName = findViewById(R.id.tvName);
        tvDateTime = findViewById(R.id.tvDateTime);
        tvClientId = findViewById(R.id.tvClientId);
        tvStatus = findViewById(R.id.tvStatus);
        tvData = findViewById(R.id.tvData);

        etTopic = findViewById(R.id.etTopic);
        etName = findViewById(R.id.etName);


        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(connectClick);
        btnConnect.setEnabled(false);

        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(disconnectClick);

        btnFilter = (Button) findViewById(R.id.btnFilter);
        btnFilter.setOnClickListener(filterClick);
        btnFilter.setEnabled(true);

        //btnSubmit.setEnabled(false);

        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString();
        tvClientId.setText(clientId);

        // Initialize the AWS Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(), // context
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_IOT_ENDPOINT);

        // The following block uses a Cognito credentials provider for authentication with AWS IoT.
        new Thread(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnConnect.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    //ADDING NOTIFICATIONS

    private void messagereceived() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String NOTIFICATION_CHANNEL_ID = "channel_1";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("WrongConstant") NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "My Notifications", NotificationManager.IMPORTANCE_MAX);
            // Configure the notification channel.
            notificationChannel.setDescription("AWS IoT notification");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.YELLOW);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher)
                .setTicker("PROJECT")
                //.setPriority(Notification.PRIORITY_MAX)
                .setContentTitle("AWS IoT Rx")
                .setContentText("Message received")
                .setContentInfo("Information");
        notificationManager.notify(1, notificationBuilder.build());
    };

    //Method for subscribing to the topic and displaying the received values.
    public void subToTopic() {

        String topic = etTopic.getText().toString();

        if(((RadioButton)findViewById(R.id.radWxdata)).isChecked())
            topic = "stec/wxdata";
        else if(((RadioButton)findViewById(R.id.radSonde)).isChecked())
            topic = "stec/sonde";

        Log.d(LOG_TAG, "topic = " + topic);

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                    new AWSIotMqttNewMessageCallback() {
                        @Override
                        public void onMessageArrived(final String topic, final byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String message = null;
                                    try {
                                        message = new String(data, "UTF-8");
                                        JSONObject json = new JSONObject(message);
                                        Log.d(LOG_TAG, "Message arrived:");
                                        Log.d(LOG_TAG, "   Topic: " + topic);
                                        Log.d(LOG_TAG, " Message: " + message);
                                        String station = null;
                                        try {
                                            station = json.getString("name");
                                        } catch (JSONException e) {
                                            station = "N/A";
                                        }
                                        String localtime = null;
                                        try {
                                            localtime = json.getString("dt");
                                        } catch (JSONException e) {
                                            localtime = "N/A";
                                        }

                                        if(name_filter.length() > 0 && !name_filter.equals("*")){
                                            if(!name_filter.equals(station))
                                                return;
                                        }

                                        ((TextView)findViewById(R.id.tvLastValues)).setText("Last Values (rec. " + java.text.DateFormat.getDateTimeInstance().format(new Date()) + "):");
                                        tvName.setText("Station: " + station);
                                        tvDateTime.setText("Station Time (UTC): " + localtime);

                                        tvData.setText("");
                                        Iterator<String> datakeys = json.keys();
                                        while(datakeys.hasNext()){
                                            String key = datakeys.next();
                                            if(!key.equals("name") && !key.equals("dt")) {
                                                tvData.append(key + " : " + json.get(key).toString() + "\n");
                                            }
                                        }

                                    } catch (UnsupportedEncodingException e) {
                                        Log.e(LOG_TAG, "Message encoding error.", e);
                                    } catch (JSONException e) {
                                        tvName.setText("ERROR: Bad JSON");
                                        tvDateTime.setText(message);
                                    }
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Subscription error.", e);
        }
    };

    View.OnClickListener connectClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Log.d(LOG_TAG, "clientId = " + clientId);

            try {

                mqttManager.connect(credentialsProvider, new AWSIotMqttClientStatusCallback() {
                    @Override
                    public void onStatusChanged(final AWSIotMqttClientStatus status, final Throwable throwable) {
                        Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (status == AWSIotMqttClientStatus.Connecting) {
                                    tvStatus.setText("Connecting...");

                                } else if (status == AWSIotMqttClientStatus.Connected) {
                                    tvStatus.setText("Connected");
                                    subToTopic();

                                } else if (status == AWSIotMqttClientStatus.Reconnecting) {
                                    if (throwable != null) {
                                        Log.e(LOG_TAG, "Connection error.", throwable);
                                    }
                                    tvStatus.setText("Reconnecting");
                                } else if (status == AWSIotMqttClientStatus.ConnectionLost) {
                                    if (throwable != null) {
                                        Log.e(LOG_TAG, "Connection error.", throwable);
                                        throwable.printStackTrace();
                                    }
                                    tvStatus.setText("Disconnected");
                                } else {
                                    tvStatus.setText("Disconnected");

                                }
                            }
                        });
                    }
                });
            } catch (final Exception e) {
                Log.e(LOG_TAG, "Connection error.", e);
                tvStatus.setText("Error! " + e.getMessage());
            }
        }
    };

    View.OnClickListener disconnectClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            try {
                mqttManager.disconnect();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Disconnect error.", e);
            }

        }
    };

    View.OnClickListener filterClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            name_filter = etName.getText().toString();

        }
    };
}