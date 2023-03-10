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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.emav1.toolspack.DHProtocol;
import com.example.emav1.toolspack.EncryptionProcessor;
import com.example.emav1.toolspack.HashProcessor;
import com.example.emav1.toolspack.PacketHandler;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity{

    public final String ACTION_USB_PERMISSION = "com.example.emav1.USB_PERMISSION";
    TextView textView;
    UsbManager usbManager;
    public UsbDevice device;
    static UsbSerialDevice serialPort;
    public UsbDeviceConnection connection;
    ImageButton  beacon, toTextMode, toContactList, toReceiverMode;
    static MutableLiveData<Boolean> isReadyToTransmitTextMessage = new MutableLiveData<>(false);


    DataBaseHelper dataBaseHelper;

    FragmentManager fragmentManager;
    FragmentTransaction fragmentTransaction;
    EncryptionProcessor encryptionProcessor = new EncryptionProcessor();

    //navbar switches
    boolean isReceiverMode;
    boolean isContactList;
    boolean isTextMessageMode;

    // Serial Receiver Variables
    private String data;
    byte[] tempArg0;
    String num;
    byte[] senderBytes, receiverBytes, sendConfirmBytes;
    String[] messagePacketArray = new String[122];
    String combinedPacketArray = "";

    private final Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    private MediaPlayer mp, beaconmp;
    boolean isRinging = false;
    boolean isDisabled = true;
    boolean isBeaconMode = false;
    String sender, receiver, message, computedHash, noHashPart, hashFromPacket, decodedData;
    boolean isFlashingSend = false;
    boolean isFlashingRecv = false;

    static BigInteger DHPublicKey;
    public static int DHPrivateKey;

    HashProcessor hashProcessor = new HashProcessor();
    PacketHandler packetHandler = new PacketHandler();

    boolean isReceivingTextPacket = false;
    boolean isAbletoNotify = false;
    boolean isPacketsComplete = false;
    int packetNumber = 0;
    int beaconsignalcounter = 0;


    /*
    This and onClickBeaconMode are the only functions you need to touch in this class.
    Mao rani ang hilabti if mag manipulate mo sa data nga ma receive from the EMA device.

    This is where all the data passed to and from the EMA device is processed.
     */
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onReceivedData(byte[] arg0) {
            //assign stream with the value of arg0, which is the value passed from the arduino.
            tempArg0 = new byte[60];
            for(int i = 0; i < arg0.length; i++){
                tempArg0[i] = arg0[i];
            }
            data = new String(arg0, StandardCharsets.UTF_8);
            // Extract Sender ID from the packet.
            num = getUserSID().trim();
            packetHandler.setSID(num);
            //tvAppend(textView, "\nUSER SID:" + packetHandler.getSenderID());
            //tvAppend(textView, "\nIN: " + data + "\nInLen: " + data.length());

            // Check if stream is not empty.
            if(arg0.length > 0) {
                // Control Code 1, Send SID to Arduino Device
                if (arg0[0] == 1) {
                    serialPort.write(packetHandler.getSIDBytes());
                    //tvAppend(textView, "OutStream : " + packetHandler.getSenderID() + "\n");
                }else if (arg0[0] == 0x02) {
                        if(data.length() == 60) {
                            // Prevent multiple instances of the infinite sound
                            if (!isRinging) {
                                // Play sound
                                beaconmp.start();
                                isRinging = true;
                            }
                            if (checkHashfromPacket()) {
                                //tvAppend(textView, "\nMatching Hashes");
                                getSenderfromPacket();
                                // Create an explicit intent for an Activity in your app
                                Intent intent = new Intent(String.valueOf(MainActivity.this));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

                                // Notification Builder
                                NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "EMABeaconNotif")
                                        .setSmallIcon(android.R.color.transparent)
                                        .setContentTitle("Emergency Beacon Signal Detected!")
                                        .setContentText("There is an emergency beacon signal detected coming from USER:" + sender)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        // Set the intent that will fire when the user taps the notification
                                        .setContentIntent(pendingIntent)
                                        .setAutoCancel(true);

                                // Notification Show
                                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                                notificationManager.notify(1, builder.build());

                                storeMessage(sender, "URGENT BEACON SIGNAL RECEIVED!", new BigInteger(String.valueOf(9)), DHPrivateKey);
                                beaconsignalcounter += 1;
                               // tvAppend(textView, "Beacon signal Received: " + beaconsignalcounter);

                                //Flashing Timer
                                beaconReceiveTimer.start();

                                // THis line is for debugging purposes
                                // Shows what is the incoming message from the arduino
                                //tvAppend(textView, "\nInStream : " + data);
                            }else
                                System.out.println("none");
                            // tvAppend(textView, "\nNon-Matching Hashes");
                        }
                    } else if (arg0[0] == 0x03) {
                        if(data.length() == 60) {
                            if (checkHashfromPacket()) {
                                FragmentTextMessage.isReceivedConfirmationByte = true;
                                FragmentTextMessage.repTimer = 4;
                                String Received = "-";
                                String Sent = FragmentMain.dateFormat.format(FragmentMain.date);
                                String PUBLICKEY="";
                                String PRIVATEKEY="";
                                dataBaseHelper.addOneMessage(getUserSID(), FragmentTextMessage.MESSAGE ,Received,Sent, PUBLICKEY, PRIVATEKEY);
                                //tvAppend(textView, "\nReceived Confirmation Byte" + data);
                                //add one message to
                            }else
                                Toast.makeText(MainActivity.this, "Wrong Hash", Toast.LENGTH_SHORT).show();
                        }
                    } else if (arg0[0] == 0x04) { // This is Sending Handshake perspective from receiver
                        getSenderfromPacket();
                        getReceiverfromPacket();
                        //getMessagefromPacket();
                        if(receiver.equals(num)) {
                            if (checkHashfromPacket()) {
                                //DHPublicKey = new BigInteger(message.substring(0,40).replace("a", ""));
                                DHPublicKey = getABfromPacket();

                                Log.d("DHKeys", "Received A : " + DHPublicKey);

                                packetHandler.setSID(sender);
                                packetHandler.setRID(num);
                                byte[] sendHandShakeBytes = new byte[60];
                                byte[] smp = new byte[1];

                                smp[0] = 0x05;
                                System.arraycopy(smp, 0, sendHandShakeBytes, 0, 1);
                                System.arraycopy(packetHandler.getSIDBytes(), 0, sendHandShakeBytes, 1, 4);
                                System.arraycopy(packetHandler.getRIDBytes(), 0, sendHandShakeBytes, 5, 4);

                                /*String tempB = new DHProtocol(DHPrivateKey).getPublicKey().toString();
                                Log.d("DHKeys", "Generated B : " + tempB);
                                for (int i = tempB.length(); i < 40; i++)
                                    tempB +=  "a";

                                 */
                                BigInteger B = new DHProtocol(DHPrivateKey).getPublicKey();
                                byte[] tempB = B.toByteArray();
                                Log.d("DHKeys", "BigInteger B Array : " + Arrays.toString(tempB));
                                // PADDING FUNCTION
                                byte[] paddedArr = new byte[40];
                                Arrays.fill(paddedArr, (byte) 126);

                                // COPY tempA to paddedArr
                                System.arraycopy(tempB, 0, paddedArr, 0, tempB.length);
                                Log.d("DHKeys", "BigInteger B Paddedd Array: "  + Arrays.toString(paddedArr));

                                System.arraycopy(paddedArr, 0, sendHandShakeBytes, 9, 40);

                                String sendHandshakeString = new String(sendHandShakeBytes, StandardCharsets.UTF_8);
                                String hash = hashProcessor.getHash(sendHandshakeString);
                                sendHandshakeString += hash;
                                System.arraycopy(hash.getBytes(), 0, sendHandShakeBytes, 52, 8);

                                serialPort.write(sendHandShakeBytes);
                                Log.d("DHKeys", "Sent Handshake Reply");
                            }
                        }
                    } else if (arg0[0] == 0x05) { // This is Receiving Handshake perspective from sender
                        getSenderfromPacket();
                        getReceiverfromPacket();
                        //getMessagefromPacket();
                        if(receiver.equals(num)) {
                            if (checkHashfromPacket()) {
                                //DHPublicKey = new BigInteger(message.substring(0,40).replace("a", ""));
                                DHPublicKey = getABfromPacket();

                                Log.d("DHKeys", "Received B : " + DHPublicKey);
                                isReadyToTransmitTextMessage.postValue(true); //Initilize with a value
                            }
                        }
                    } else if (arg0[0] >= 0x06) { // This is for Text Message Mode
                    if(data.length() == 60) {
                        //getSenderfromPacket();
                        getReceiverfromPacket();
                        getMessagefromPacket();
                        if(receiver.equals(num)){
                            if (checkHashfromPacket()) {
                                // ... decryption for display, and store it in a temporary string.
                                // ... store to messages table in database encrypted
                                // if(regular message)

                                byte[] cipherInBase64 = new byte[43];
                                System.arraycopy(tempArg0,9,cipherInBase64,0,43);
                                String base64CipherinString = new String(cipherInBase64, StandardCharsets.UTF_8);
                                byte[] encryptedData = Base64.decode(base64CipherinString, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_CLOSE);
                                Log.d("DHKeys","Paramter Public : " + DHPublicKey);
                                encryptionProcessor.receivingEncryptionProcessor(encryptedData, DHPublicKey, DHPrivateKey);
                                Log.d("DHKeys","Parameter Private : " + DHPrivateKey);
                                decodedData = encryptionProcessor.getDecodedText();
                                sender = packetHandler.getID(packetHandler.formatID("09" + decodedData.substring(0,9)));

                                storeMessage(sender, base64CipherinString, DHPublicKey, DHPrivateKey);
                                Log.d("ADebugTag","Received Single Packet Text Message");
                                messagePacketArray[packetNumber] = decodedData.subSequence(9,decodedData.length()).toString();


                                if(packetNumber == 0){
                                    isReceivingTextPacket = true;
                                    Log.d("ADebugTag","Receiving Packets");
                                }

                                if(arg0[0] == 0x7F){
                                    isPacketsComplete = true;
                                }else
                                    Log.d("ADebugTag","Not Stop Byte");

                                if(isPacketsComplete) {
                                    final Handler handler = new Handler(Looper.getMainLooper());
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            //Do something after 100ms
                                            //Confirmation packet segment
                                            packetHandler.setSID(sender);
                                            packetHandler.setRID(num);
                                            sendConfirmBytes = new byte[60];
                                            byte[] smp = new byte[1];

                                            smp[0] = 0x03;
                                            System.arraycopy(smp, 0, sendConfirmBytes, 0, 1);
                                            System.arraycopy(packetHandler.getSIDBytes(), 0, sendConfirmBytes, 1, 4);
                                            System.arraycopy(packetHandler.getRIDBytes(), 0, sendConfirmBytes, 5, 4);
                                            System.arraycopy("0000000000000000000000000000000000000000000".getBytes(), 0, sendConfirmBytes, 9, 40);

                                            String string = new String(sendConfirmBytes, StandardCharsets.UTF_8);
                                            String hash = hashProcessor.getHash(string);
                                            string += hash;
                                            System.arraycopy(hash.getBytes(), 0, sendConfirmBytes, 52, 8);

                                            serialPort.write(sendConfirmBytes);

                                            Log.d("ADebugTag","ConfirmPacket : " + string + "\nPacketLen: " +  "\nHash: " + hash);

                                        }
                                    }, 1000);


                                    // THis line is for debugging purposes
                                    // Shows what is the incoming message from the arduino
                                    //tvAppend(textView, "\nHashFromPacket : " + hashFromPacket + "\nComputedHash = " + computedHash);

                                    //Reset TextMessagePacket
                                    isPacketsComplete = false;
                                    isReceivingTextPacket = false;
                                    isAbletoNotify = true;

                                }else{
                                    packetNumber += 1;
                                }

                                if(isAbletoNotify) {

                                    String s = "";
                                    for (int i = 0; i <packetNumber; i++) {
                                        combinedPacketArray.concat(messagePacketArray[i]);
                                    }
                                    // DO THIS AFTER CONFIRMING
                                    // Notify User of Received Packet
                                    mp.start(); // Play sound
                                    // Create an explicit intent for an Activity in your app
                                    Intent intent = new Intent(String.valueOf(MainActivity.this));
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, 0);

                                    // Notification Builder
                                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, "EMAMessageNotif")
                                            .setSmallIcon(android.R.color.transparent)
                                            .setContentTitle("Message from User: " + sender)
                                            .setContentText("Message: " + combinedPacketArray)
                                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                            // Set the intent that will fire when the user taps the notification
                                            .setContentIntent(pendingIntent)
                                            .setAutoCancel(true);

                                    // Notification Show
                                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
                                    notificationManager.notify(2, builder.build());

                                    isPacketsComplete = false;
                                    isAbletoNotify = false;
                                    combinedPacketArray = "";
                                    packetNumber = 0;
                                    messagePacketArray = new String[122];
                                }
                            } else {
                                Log.d("ADebugTag","HashFromPacket : " + hashFromPacket + "\nComputedHash = " + computedHash);
                            }
                        }else
                            Log.d("ADebugTag","Mismatch num\nnum From Packet : " + sender );

                    }
                }
            }
        }
    };

    CountDownTimer beaconReceiveTimer = new CountDownTimer(3000, 1000) {
        @Override
        public void onTick(long l) {
            if(isFlashingRecv) {
                beacon.setColorFilter(Color.rgb(13, 16, 19));
                isFlashingRecv = false;
            }else {
                beacon.setColorFilter(Color.rgb(255, 25, 0));
                isFlashingRecv = true;
            }
        }

        @Override
        public void onFinish() {
            beaconReceiveTimer.cancel();
            beaconReceiveTimer.start();
        }
    };

    // This function handles what happens when the beacon mode button is clicked.
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onClickBeaconMode(View view){
            if (isRinging) {
                beaconReceiveTimer.cancel();
                beacon.setColorFilter(Color.rgb(13, 16, 19));
                try {
                    beaconmp.stop();
                    beaconmp.prepare();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isRinging = false;
                Toast.makeText(MainActivity.this, "Emergency signal from " + sender, Toast.LENGTH_LONG).show();
            } else {
                try {
                    if (beacon.isEnabled()) {
                        if (!isDisabled) {

                            beaconSendTimer.cancel();
                            isDisabled = true;
                            //Reset Color
                            beacon.setColorFilter(Color.rgb(13, 16, 19));
                            isFlashingSend = false;
                            Toast.makeText(MainActivity.this, "Beacon Mode OFF", Toast.LENGTH_SHORT).show();

                        } else {

                            /*
                            The 'string' is similar to the packet assignment mentioned in the Manuscript
                            | SMP-1 | RID-4 | SID-4 | DATA-45 | HK-11 |  ----> This totals to 64bytes-1packet
                           ____________________________________________________________________________
                           Upon further testing, the arduino buffer is actually just up to the 11 on the last set of numbers above. We have to work with that.
                           New format:
                           | SMP - 1 | RID - 4 | SID - 4 | DATA - 40 | HK - 11 |
                             */
                            //Countdown timer to disable sending for 3 seconds
                            Toast.makeText(MainActivity.this, "Beacon Mode ON", Toast.LENGTH_SHORT).show();
                            isDisabled = false;
                            beaconSendTimer.start();
                            storeMessage(getUserSID(), "URGENT BEACON SIGNAL SENT!", new BigInteger(String.valueOf(69)), 69);
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Please Connect the EMA Device!", Toast.LENGTH_SHORT).show();
                }
            }

    }

    CountDownTimer beaconSendTimer = new CountDownTimer(3000, 1000) {

        @Override
        public void onTick(long l) {
            if(isFlashingSend) {
                beacon.setColorFilter(Color.rgb(13, 16, 19));
                isFlashingSend = false;
            }else {
                beacon.setColorFilter(Color.rgb(25, 255, 0));
                isFlashingSend = true;
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onFinish() {
            byte[] sendBeaconBytes = new byte[60];
            packetHandler.setSID(getUserSID().trim());
            byte[] smp = new byte[1];
            smp[0] = 0x02;

            System.arraycopy(smp, 0, sendBeaconBytes, 0, 1);
            System.arraycopy("0000".getBytes(), 0, sendBeaconBytes, 1, 4);
            System.arraycopy(packetHandler.getSIDBytes(), 0, sendBeaconBytes, 5, 4);
            System.arraycopy("0000000000000000000000000000000000000000".getBytes(), 0, sendBeaconBytes, 9, 40);

            String string = new String(smp, StandardCharsets.UTF_8) + "0000" + new String(packetHandler.getSIDBytes(), StandardCharsets.UTF_8) + "00000" + "00000" + "00000" + // Data
                  "00000" + "00000" + "00000" + "00000" + "00000" + "000"; // + "123456" + "78911"

            String beaconHash = hashProcessor.getHash(string);
            String beaconMessage = string + beaconHash;

            System.arraycopy(beaconHash.getBytes(), 0, sendBeaconBytes, 52, 8);

            serialPort.write(sendBeaconBytes);
            //tvAppend(textView, "\nINFO:\n" + beaconMessage + "\nSID: " + packetHandler.getSenderID() + "\nMessageLen: " + string.length() + "\nPacketLen: " + new String(sendBeaconBytes, StandardCharsets.UTF_8).length());
            beaconSendTimer.cancel();
            beaconSendTimer.start();
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);     // Only use light mode
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createBeaconNotificationChannel();      // start notification channels
        createMessageNotificationChannel();

        usbManager = (UsbManager) getSystemService(USB_SERVICE);
       // textView = findViewById(R.id.main_serialMonitor);
        beacon = findViewById(R.id.main_beaconButton);
        toTextMode = findViewById(R.id.toTextModeButton);
        toContactList = findViewById(R.id.toContactList);
        toReceiverMode = findViewById(R.id.toReceiverModeButton);
       // textView.setMovementMethod(new ScrollingMovementMethod());

        dataBaseHelper = new DataBaseHelper(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
//        textView.setMovementMethod(new ScrollingMovementMethod());

        //DHPrivateKey = new Random().nextInt(56-41) + 41; // Randomly generated everytime the app is opened upto 40 Characters
        DHPrivateKey = new Random().nextInt(140-41) + 41; // up to 40bytes

        mp = MediaPlayer.create(MainActivity.this, notificationSound);
        beaconmp = MediaPlayer.create(MainActivity.this, R.raw.emergency_alarm);
        beaconmp.setLooping(true);

        setReceiverModeColor();
        if(!arduinoConnected()){
            beacon.setColorFilter(Color.rgb(13, 16, 19));
            beacon.setEnabled(true);
        }else
            beacon.setEnabled(false);

    }

    // This initializes the broadcast receiver whenever the EMA Device is connected to the phone.
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
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
                    beacon.setColorFilter(Color.rgb(13, 16, 19));
                    beacon.setEnabled(true);
                    arduinoConnected();
                    Toast.makeText(MainActivity.this, "EMA device connected!", Toast.LENGTH_SHORT).show();
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    // MainActivity
                    beacon.setColorFilter(Color.rgb(175, 175, 175));
                    beacon.setEnabled(false);
                    isDisabled = true;
                    isFlashingSend = false;
                    isBeaconMode = false;
                    isFlashingRecv = false;
                    try {
                        beaconSendTimer.cancel();
                        beaconReceiveTimer.cancel();
                        beaconmp.stop();
                        beaconmp.prepare();
                        mp.stop();
                        mp.prepare();
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                    isRinging = false;

                    //Fragment Text Message
                    FragmentTextMessage.repTimer = 0;
                    FragmentTextMessage.isReceivedConfirmationByte = false;

                    arduinoDisconnected();
                    Toast.makeText(MainActivity.this, "EMA device disconnected!", Toast.LENGTH_SHORT).show();
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

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public BigInteger getABfromPacket(){
        byte[] AB = new byte[40];
        System.arraycopy(tempArg0,9,AB,0,40); //copy

        int length = getBigIntLength(AB);
        byte[] finalAB = trimPaddedBigInt(AB);

        Log.d("DHKeys", "BigInteger Dispadded Converted : "  + Arrays.toString(finalAB));
        return new BigInteger(finalAB);
    }

    public void arduinoDisconnected() {
        try{
            serialPort.close();
        }catch(Exception e){
            Toast.makeText(MainActivity.this, "Failed to close Serial Port", Toast.LENGTH_SHORT).show();
        }
    }

    /*private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(() -> ftv.append(ftext));
    }

     */


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

        cursor.close();
        return SID;
    }

    // This will be called in FragmentTextMessage and mCallback to store messages to database.
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void storeMessage(String ID, String MESSAGE, BigInteger PUBLICKEY, int PRIVATEKEY){
        String Received = FragmentMain.dateFormat.format(FragmentMain.date);
        String Sent = "-";

        dataBaseHelper.addOneMessage(ID, MESSAGE, Received,Sent, PUBLICKEY.toString(), Integer.toString(PRIVATEKEY));

        //refill the contact Array lists so that the Contact ID will be filled with the new information
        FragmentMain.messageID.clear();
        FragmentMain.messageNames.clear();
        FragmentMain.messageNum.clear();
        FragmentMain.messageText.clear();
        FragmentMain.messageSent.clear();
        FragmentMain.messageReceived.clear();
        FragmentMain.storeDBtoArrays();

        // run on ui thread is needed to avoid crash when updating the recycler view
        runOnUiThread(() -> {
            FragmentMain.inboxListAdapter.notifyDataSetChanged();
            // Stuff that updates the UI
        });

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

    private void getSenderfromPacket(){
        senderBytes = new byte[4];
        sender = "";

        System.arraycopy(tempArg0, 5, senderBytes, 0,4);

        sender = packetHandler.getID(senderBytes);
        //tvAppend(textView, "\nSenderFromPacket: " + sender);

    }

    private void getReceiverfromPacket(){
        receiverBytes = new byte[4];
        receiver = "";

        System.arraycopy(tempArg0, 1, receiverBytes, 0,4);

        receiver = packetHandler.getID(receiverBytes);
        //tvAppend(textView, "\nReceiverFromPacket: " + receiver);

    }

    private void getMessagefromPacket(){
        message = "";

        for(int i = 0; i < 43; i++){
                message = message.concat(String.valueOf(data.charAt(i+9)));
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private boolean checkHashfromPacket(){
        boolean isHashMatched;
        byte[] hashFromPacketBytes = new byte[8];
        byte[] noHashPartFromPacketBytes = new byte[52];
        noHashPart = "";
        hashFromPacket = "";
        computedHash = "";

        System.arraycopy(tempArg0,52,hashFromPacketBytes,0,8);
        System.arraycopy(tempArg0,0,noHashPartFromPacketBytes,0,52);
        hashFromPacket = new String(hashFromPacketBytes);
        computedHash = hashProcessor.getHash(new String(noHashPartFromPacketBytes,StandardCharsets.UTF_8));
        //tvAppend(textView, "\nComputed: " + computedHash + "\nFromPkt:" + hashFromPacket);

        if(hashFromPacket.equals(computedHash))
            isHashMatched = true;
        else
            isHashMatched = false;

        return isHashMatched;
    }

    int getBigIntLength(byte[] arr){
        int length = 0;
        for(int i = 0; i<40; i++){
            if(arr[i] == 126)
                length++;
        }

        return length;
    }

    byte[] trimPaddedBigInt(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 126)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }


    public void onBackPressed() {
        //doing nothing on pressing Back key
    }

}