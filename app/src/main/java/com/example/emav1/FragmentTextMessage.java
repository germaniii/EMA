package com.example.emav1;

import android.content.Context;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.emav1.toolspack.HashProcessor;
import com.felhr.usbserial.UsbSerialDevice;

import java.util.ArrayList;

public class FragmentTextMessage extends Fragment {

    public final String ACTION_USB_PERMISSION = "com.example.emav1.USB_PERMISSION";
    TextView textView;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    ImageButton sendButton;
    EditText message;
    Spinner number;
    ArrayList<String> spinnerContacts;
    DataBaseHelper dataBaseHelper;

    String SMP, SID, RID, MESSAGE;
    String MESSAGE_FINAL_2, HK2;
    String textPacket;
    boolean isDisabled = false;

    static int repTimer = 0; // max of 2
    static boolean isReceivedConfirmationByte = false;

    Context context;
    HashProcessor hashProcessor = new HashProcessor();

    /*final Handler handler = new Handler();

    Runnable sendTextMessage = new Runnable() {
        @Override
        public void run() {
            for(int i = 0; i < 10; i++){
                if(){

                }
            }
            handler.postDelayed(this, 0);  // 1 second delay
        }
    };

    Runnable sendPacket = new Runnable() {
        @Override
        public void run() {
            MainActivity.serialPort.write(textPacket.getBytes());
            if (repTimer == 4){
                isDisabled = false;
                repTimer = 0;
                Toast.makeText(context, "Successfully sent message to "
                        + RID, Toast.LENGTH_SHORT).show();
                handler.removeCallbacks(this);

                //if message is longer than 44 characters, add a function here to send the next packet.
            }else if(repTimer < 3){
                repTimer++;
                handler.postDelayed(this, 1000);
            }else if (repTimer == 3){
                // if needed, add a notification part here
                isDisabled = false;
                repTimer = 0;
                Toast.makeText(context, "Failed to send message to " + RID, Toast.LENGTH_SHORT).show();
            }else{
                repTimer = 0;
                isDisabled = false;
                handler.removeCallbacks(this);
            }
        }
    };
    handler.post(runnable);
     */

    /*
    This is the only thing you need to touch in this class.
    This handles when the Send Button is being pressed.

    To implement:
        - Background process of sending?
        - if not, Loading screen to wait until all the message packets are successfully sent?

     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void onClickSendButton(View view) {
        try {
            if (sendButton.isEnabled()){
                if ((message.getText().length() == 0) || number.getSelectedItem() == "") {
                    Toast.makeText(context, "Please Fill Up All Fields!", Toast.LENGTH_SHORT).show();
                }else {
                    if(!isDisabled) {
                        SMP = "3";
                        getSID();
                        getRID();
                        String MESSAGE = message.getText().toString().trim();
                        String MESSAGE_FINAL = MESSAGE;
                        String HK;

                        //text = String.valueOf(Integer.parseInt(text.substring(0,text.length())) + 1);
                        // To increment SMP

                        //   New format:
                        //   | SMP - 1 | RID - 4 | SID - 4 | DATA - 40 | HK - 11 |

                        //if message entered is less than 40 characters, add whitespace characters to fill up the packet.
                        if (MESSAGE.length() < 40) {
                            for (int i = 0; i < 40 - MESSAGE.length(); i++)
                                MESSAGE_FINAL = MESSAGE_FINAL.concat(".");
                        }

                        String textMessage = SMP+RID+SID+MESSAGE_FINAL;
                        HK = hashProcessor.getHash(textMessage);
                        textPacket = textMessage + HK;
                        MESSAGE_FINAL_2 = MESSAGE_FINAL;
                        HK2 = HK;

                        //if message entered is more than 40 characters, splice. <--- Optional
                        /*
                        if(string.length() > 40){
                            int numberOfPackets = string.length()/44;
                            for(int i = 0; i < numberOfPackets; i++){
                                //this code will loop until all packets are sent.
                            }
                        }
                        */



                        //Should use the serial port from MainActivity to reference the registered serialPort Arduino
                        MainActivity.serialPort.write((textPacket).getBytes());
                        tvAppend(textView, "ML:" + textPacket.length() +
                                "\n" + textPacket + "\n");
                        Toast.makeText(context, "Transmitted", Toast.LENGTH_SHORT).show();

                        // prevent multiple send touches
                        isDisabled = true;

                        //Start repitition Counter

                        //handler.postRunnable
                        countDownTimer.start();
                    }else{
                        Toast.makeText(context, "A message is still sending, please try again later.", Toast.LENGTH_SHORT).show();
                    }

                    //Countdown timer to wait for variable change (confirmation byte received.)
                    // Max repetition would be 3? times
                }
            }else
                Toast.makeText(context, "Synchronizing EMA Device, Please Wait", Toast.LENGTH_SHORT).show();
        }catch (Exception e){
            //Toast.makeText(context, "Please Connect EMA Device", Toast.LENGTH_SHORT).show();
        }
    }

     CountDownTimer countDownTimer = new CountDownTimer(3000, 1000) {
        @Override
        public void onTick(long l) {
            if(isReceivedConfirmationByte) // this will stop the counting
                repTimer = 4;
        }

        @Override
        public void onFinish() {
            MainActivity.serialPort.write((SMP + RID + SID + MESSAGE_FINAL_2 + HK2).getBytes());
            countDownRepeater();
        }
    };

    private void countDownRepeater(){
        if (repTimer == 4){
            countDownTimer.cancel();
            isDisabled = false;
            repTimer = 0;
            Toast.makeText(context, "Successfully sent message to "
                    + RID, Toast.LENGTH_SHORT).show();

            //if message is longer than 44 characters, add a function here to send the next packet.
        }else if(repTimer < 3){
            repTimer++;
            countDownTimer.cancel();
            countDownTimer.start();
        }else if (repTimer == 3){
            // if needed, add a notification part here
            isDisabled = false;
            countDownTimer.cancel();
            repTimer = 0;
            Toast.makeText(context, "Failed to send message to " + RID, Toast.LENGTH_SHORT).show();
        }else{
            repTimer = 0;
            isDisabled = false;
            countDownTimer.cancel(); // for error trapping (stop the loop)
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_text_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.context = getActivity();

        textView = getActivity().findViewById(R.id.main_serialMonitor);
        sendButton = getActivity().findViewById(R.id.textMessage_sendButton);
        message = getActivity().findViewById(R.id.textMessage_message);

        dataBaseHelper = new DataBaseHelper(context);
        spinnerContacts = new ArrayList<>();
        storeDBtoArrays();


        number = getActivity().findViewById(R.id.textMessage_Spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, spinnerContacts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        number.setAdapter(adapter);

        // Send Button On Click Listener
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v)
            {
                onClickSendButton(v);
            }
        });

    }


    // This function retrieves the contact number (SID) of the user.
    void getSID(){
        Cursor cursor = dataBaseHelper.readUserSID();
        if(cursor.getCount() < 1){
            Toast.makeText(context, "Error Getting SID", Toast.LENGTH_SHORT).show();
        }else{
            if(cursor.moveToFirst()){
                    SID = cursor.getString(0);  //Names
                    tvAppend(textView, SID+"\n");
            }
        }
    }

    // This function retrieves the RID of the contact that is selected.
    void getRID(){
        Cursor cursor = dataBaseHelper.readContactNumber(number.getSelectedItem().toString());
        if(cursor.getCount() < 1){
            Toast.makeText(context, "Error Getting RID", Toast.LENGTH_SHORT).show();
        }else{
            if(cursor.moveToFirst()){
                    RID = cursor.getString(0);  //Names
            }
                    tvAppend(textView, "Name:" + number.getSelectedItem().toString() + RID+"\n");
        }
    }


    void storeDBtoArrays(){
        Cursor cursor = dataBaseHelper.readAllDataContactsTable();
        if(cursor.getCount() <= 1){
            Toast.makeText(context, "No Contacts Found!", Toast.LENGTH_SHORT).show();
        }else{
            if(cursor.moveToFirst()){
                while (cursor.moveToNext()){
                    spinnerContacts.add(cursor.getString(1));  //Names
                }
            }
        }
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }



}