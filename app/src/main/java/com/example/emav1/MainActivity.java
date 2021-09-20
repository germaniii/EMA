package com.example.emav1;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.emav1.toolspack.PacketHandler;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity{

    public final String ACTION_USB_PERMISSION = "com.example.emav1.USB_PERMISSION";
    TextView textView;
    UsbManager usbManager;
    public UsbDevice device;
    static UsbSerialDevice serialPort;
    public UsbDeviceConnection connection;
    ImageButton  beacon, toTextMode, toContactList, toReceiverMode;

    PacketHandler packetHandler;

    DataBaseHelper dataBaseHelper;

    FragmentManager fragmentManager;
    FragmentTransaction fragmentTransaction;
    FragmentMain fragmentMain;

    //navbar switches
    boolean isReceiverMode;
    boolean isContactList;
    boolean isTextMessageMode;

    // Serial Receiver Variables
    private String data;
    private byte[] stream = new byte[100];

    private Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    private MediaPlayer mp;
    boolean isRinging = false;
    String sender, message;


    /*
    This and onClickBeaconMode are the only functions you need to touch in this class.
    Mao rani ang hilabti if mag manipulate mo sa data nga ma receive from the EMA device.

    This is where all the data passed to and from the EMA device is processed.

    To implement:
        - Twofish Algorithm
        - JH

   Finished:
        - Check if signal is emergency, and play the emergency sound in R.raw
     */
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onReceivedData(byte[] arg0) {
            String num = getUserSID().trim();
            try {
                //assign stream with the value of arg0, which is the value passed from the arduino.
                stream = arg0;
                data = new String(arg0, "UTF-8");
                // Extract Sender ID from the packet.
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // Check if stream is not empty.
            if(stream.length > 0) {
                // Control Code 1, Send SID to Arduino Device
                if (stream[0] == 1) {
                    serialPort.write(num.getBytes());
                    tvAppend(textView, "OutStream : " + num + "\n");

                } else if (data.charAt(0) == '0') {
                    getDetailsfromPacket();

                    // Play sound
                    mp = MediaPlayer.create(MainActivity.this, R.raw.emergency_alarm);
                    mp.setLooping(true);
                    mp.start();
                    isRinging = true;

                    // Create an explicit intent for an Activity in your app
                    Intent intent = new Intent(String.valueOf(MainActivity.this));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

                    // Notification Builder
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "EMABeaconNotif")
                            .setSmallIcon(R.drawable.icon_ema)
                            .setContentTitle("Emergency Beacon Signal Detected!")
                            .setContentText("There is an emergency beacon signal detected coming from USER:" + sender)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            // Set the intent that will fire when the user taps the notification
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true);

                    // Notification Show
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                    notificationManager.notify(1, builder.build());

                    // THis line is for debugging purposes
                    // Shows what is the incoming message from the arduino
                    tvAppend(textView, "\nInStream : " + data);

                } else if (data.charAt(0) >= '2') {
                    getDetailsfromPacket();
                    // ... decryption for display, and store it in a temporary string.
                    // ... notification function
                    // ... store to messages table in database encrypted
                    // if(regular message)
                    mp = MediaPlayer.create(MainActivity.this, notificationSound);
                    mp.start(); // Play sound

                    // Create an explicit intent for an Activity in your app
                    Intent intent = new Intent(String.valueOf(MainActivity.this));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

                    // Notification Builder
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "EMAMessageNotif")
                            .setSmallIcon(R.drawable.icon_ema)
                            .setContentTitle("Message from User: " + sender)
                            .setContentText("Message: " + message)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            // Set the intent that will fire when the user taps the notification
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true);

                    // Notification Show
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                    notificationManager.notify(2, builder.build());

                    //Storing to Messages Table Database
                    storeMessage(sender, message);

                    // THis line is for debugging purposes
                    // Shows what is the incoming message from the arduino
                    tvAppend(textView, "\nInStream : " + data);

                }
            }
        }
    };

    // This function handles what happens when the beacon mode button is clicked.
    public void onClickBeaconMode(View view){
        if(isRinging){
            mp.stop();
            try {
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            isRinging = false;
            Toast.makeText(MainActivity.this, "Emergency signal from " + sender, Toast.LENGTH_LONG).show();
        }else {
            try {
                if (beacon.isEnabled()) {
                    String string = "0" + "0000" + getUserSID() + "00000" + "00000" + "00000" + // Data
                            "00000" + "00000" + "00000" + "00000" + "00000" + "12345678911"; // <-- this HK part will be replaced later on when HK algorithm is finished
                    /*
                        The 'string' is similar to the packet assignment mentioned in the Manuscript
                        | SMP-1 | RID-4 | SID-4 | DATA-45 | HK-11 |  ----> This totals to 64bytes-1packet

                       ____________________________________________________________________________

                       Upon further testing, the arduino buffer is actually just up to the 11 on the last set of numbers above. We have to work with that.

                       New format:
                       | SMP - 1 | RID - 4 | SID - 4 | DATA - 40 | HK - 11 |

                     */
                    serialPort.write(string.getBytes());
                    tvAppend(textView, "\nINFO:\n" + string + "\n");
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Please Connect the EMA Device!", Toast.LENGTH_SHORT).show();
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);     // Only use light mode
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createBeaconNotificationChannel();      // start notification channels
        createMessageNotificationChannel();

        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        textView = findViewById(R.id.main_serialMonitor);
        beacon = findViewById(R.id.main_beaconButton);
        toTextMode = findViewById(R.id.toTextModeButton);
        toContactList = findViewById(R.id.toContactList);
        toReceiverMode = findViewById(R.id.toReceiverModeButton);
        textView.setMovementMethod(new ScrollingMovementMethod());
        packetHandler = new PacketHandler();

        dataBaseHelper = new DataBaseHelper(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
        textView.setMovementMethod(new ScrollingMovementMethod());
        setReceiverModeColor();

        // Set Beacon Image Whenever Transmission Device is Connected
        if(!arduinoConnected()) {
            //beacon.setImageResource(R.drawable.icon_beacon_on);
        }
    }

    // This initializes the broadcast receiver whenever the EMA Device is connected to the phone.
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                    boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) {
                        connection = usbManager.openDevice(device);
                        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                        if (serialPort != null) {
                            if (serialPort.open()) { //Set Serial Connection Parameters.
                                serialPort.setBaudRate(9600);
                                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                                serialPort.read(mCallback);
                            } else {
                                Log.d("SERIAL", "PORT NOT OPEN");
                            }
                        } else {
                            Log.d("SERIAL", "PORT IS NULL");
                        }
                    } else {
                        Log.d("SERIAL", "PERM NOT GRANTED");
                    }
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    arduinoConnected();
                    Toast.makeText(MainActivity.this, "EMA device connected!", Toast.LENGTH_SHORT).show();
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    arduinoDisconnected();
                    Toast.makeText(MainActivity.this, "EMA device disconnected!", Toast.LENGTH_SHORT).show();
                }
            }catch (Exception e){
                Toast.makeText(MainActivity.this, "EMA Device Disconnected!", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // This function is called whenever the EMA device is connected
    public boolean arduinoConnected() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        boolean keep = true;
        if (!usbDevices.isEmpty()) {
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x1A86 || deviceVID == 0x2341 || deviceVID == 0x0403)//Arduino UNO and Nano Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
        return keep;
    }

    public void arduinoDisconnected() {
        try{
            serialPort.close();
        }catch(Exception e){
            Toast.makeText(MainActivity.this, "Failed to close Serial Port", Toast.LENGTH_SHORT).show();
        }
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(() -> ftv.append(ftext));
    }


    /*
     This function handles the changing of the ui
     from Contact List to Inbox List and Text Message Mode
     */
    public void ChangeFragment(View view){
        if (view == findViewById(R.id.toContactList) && !isContactList) {
            fragmentManager = getSupportFragmentManager();
            fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setReorderingAllowed(true)
                    .setCustomAnimations(
                            R.anim.slide_to_right,  // enter
                            R.anim.fade_out  // popExit
                    )
                    .remove(this.getSupportFragmentManager().findFragmentById(R.id.fragment_container_view))
                    .replace(R.id.fragment_container_view, FragmentContactList.class, null, "ContactList")
                    .commit();

            //set navbar switches
            setContactListColor();

        }

        if(view == findViewById(R.id.toTextModeButton) && !isTextMessageMode){
            fragmentManager = getSupportFragmentManager();
            fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setReorderingAllowed(true)
                    .setCustomAnimations(
                            R.anim.slide_to_left,  // enter
                            R.anim.fade_out // popExit
                    )
                    .remove(this.getSupportFragmentManager().findFragmentById(R.id.fragment_container_view))
                    .replace(R.id.fragment_container_view, FragmentTextMessage.class, null, "TextMessageMode")
                    .commit();

            //set navbar switches
            setTextMessageColor();
        }

        if(view == findViewById(R.id.toReceiverModeButton) && !isReceiverMode){
            fragmentManager = getSupportFragmentManager();
            fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setReorderingAllowed(true)
                    .setCustomAnimations(
                            R.anim.slide_to_down,  // enter
                            R.anim.fade_out // popExit
                    )
                    .remove(this.getSupportFragmentManager().findFragmentById(R.id.fragment_container_view))
                    .replace(R.id.fragment_container_view, FragmentMain.class, null, "ReceiverMode")
                    .commit();

            //set navbar switches
            setReceiverModeColor();
        }
    }

    //for use when changing fragments to change the navbar colors
    public void setContactListColor(){
        toContactList.setColorFilter(ContextCompat.getColor(this, R.color.bluegreen));
        toReceiverMode.setColorFilter(ContextCompat.getColor(this, R.color.white));
        toTextMode.setColorFilter(ContextCompat.getColor(this, R.color.white));
        isContactList = true;
        isReceiverMode = false;
        isTextMessageMode = false;
    }
    public void setReceiverModeColor(){
        toContactList.setColorFilter(ContextCompat.getColor(this, R.color.white));
        toReceiverMode.setColorFilter(ContextCompat.getColor(this, R.color.bluegreen));
        toTextMode.setColorFilter(ContextCompat.getColor(this, R.color.white));
        isContactList = false;
        isReceiverMode = true;
        isTextMessageMode = false;
    }
    public void setTextMessageColor(){
        toContactList.setColorFilter(ContextCompat.getColor(this, R.color.white));
        toReceiverMode.setColorFilter(ContextCompat.getColor(this, R.color.white));
        toTextMode.setColorFilter(ContextCompat.getColor(this, R.color.bluegreen));
        isContactList = false;
        isReceiverMode = false;
        isTextMessageMode = true;
    }

    // This is a database handler to get the User SID whenever the Arduino is connected.
    String getUserSID(){
        String SID = null;
        Cursor cursor;
        cursor = dataBaseHelper.readUserSID();

        if (cursor.getCount() == 0) {
            Toast.makeText(MainActivity.this, "No User SID!", Toast.LENGTH_SHORT).show();
        } else {
            if(cursor.moveToFirst()){
                SID = cursor.getString(0);     //CONTACT NUM
                    while(cursor.moveToNext())
                        SID = cursor.getString(0);     //CONTACT NUM
            }
        }
        return SID;
    }

    // This will be called in FragmentTextMessage and mCallback to store messages to database.
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void storeMessage(String ID, String MESSAGE){
        String Received = FragmentMain.dateFormat.format(FragmentMain.date);
        String Sent = "-";

        dataBaseHelper.addOneMessage(ID, MESSAGE, Received,Sent);

        //refill the contact Array lists so that the Contact ID will be filled with the new information
        FragmentMain.messageID.clear();
        FragmentMain.messageNames.clear();
        FragmentMain.messageNum.clear();
        FragmentMain.messageText.clear();
        FragmentMain.messageSent.clear();
        FragmentMain.messageReceived.clear();
        FragmentMain.storeDBtoArrays();
        FragmentMain.inboxListAdapter.notifyDataSetChanged();

    }

    private void createBeaconNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "EMABeaconNotifChannel";
            String description = "Handles Beacon notifications for EMA App";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("EMABeaconNotif", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createMessageNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "EMAMessageNotifChannel";
            String description = "Handles Message notifications for EMA App";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("EMAMessageNotif", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void getDetailsfromPacket(){
        sender = "";
        message = "";

        for(int i = 0; i < 4; i++){
            sender = sender.concat(String.valueOf(data.charAt(i+5))).trim();
        }
        for(int i = 0; i < 40; i++){
            message = message.concat(String.valueOf(data.charAt(i+9)));
        }
    }


    public void onBackPressed() {
        //doing nothing on pressing Back key
    }
}