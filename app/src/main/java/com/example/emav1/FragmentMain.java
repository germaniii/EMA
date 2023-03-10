package com.example.emav1;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.emav1.toolspack.EncryptionProcessor;

import java.util.ArrayList;
import java.util.Date;

public class FragmentMain extends Fragment  implements InboxListAdapter.ItemClickListener{

    View view;

    static InboxListAdapter inboxListAdapter;
    static ArrayList<String> messageID, messageNames,messageNum,messageText,messageReceived, messageSent;
    private RecyclerView recyclerView;
    TextView dialog_name, dialog_num, dialog_mess, dialog_date;
    EditText uName, uNumber;
    static Date date;
    static SimpleDateFormat dateFormat;
    static DataBaseHelper dataBaseHelper;

    Context context;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.context = getActivity();
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        date = Calendar.getInstance().getTime();
        dateFormat = new SimpleDateFormat("MM-dd-yyyy | hh:mm");

        // data to populate the RecyclerView with
        messageID = new ArrayList<>();
        messageNames = new ArrayList<>();
        messageNum = new ArrayList<>();
        messageText = new ArrayList<>();
        messageSent = new ArrayList<>();
        messageReceived = new ArrayList<>();

        dataBaseHelper = new DataBaseHelper(context);
        storeDBtoArrays();

        // set up the RecyclerView
        recyclerView = getActivity().findViewById(R.id.main_inboxList);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        linearLayoutManager.setReverseLayout(true);
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        inboxListAdapter = new InboxListAdapter(context, messageNames, messageNum, messageText, messageReceived, messageSent);
        inboxListAdapter.setClickListener(this);
        recyclerView.setAdapter(inboxListAdapter);

        //Checks if Contacts is Empty, if yes, will ask for user's contact number(last 4 digits)
        if(!dataBaseHelper.readAllDataContactsTable().moveToFirst()){
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("User Set-up")
                    .setMessage("\nPlease input your name and the your phone number (09xx-xxx-xxxx).");
            // I'm using fragment here so I'm using getView() to provide ViewGroup
            // but you can provide here any other instance of ViewGroup from your Fragment / Activity
            View viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_usercontact, (ViewGroup) getActivity().findViewById(android.R.id.content), false);
            // Set up the input
            //final EditText input = (EditText) viewInflated.findViewById(R.id.input);
            uName = viewInflated.findViewById(R.id.dialog_uName);
            uNumber = viewInflated.findViewById(R.id.dialog_uNumber);
            builder.setView(viewInflated);

            // Set up the buttons
            builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(uName.getText().toString().equals("") || uNumber.getText().toString().equals("") || uNumber.getText().toString().length() < 11){
                        Toast.makeText(context, "Please fill up all fields!", Toast.LENGTH_SHORT).show();
                    }else{
                        String strDate = null;
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            strDate = dateFormat.format(date);
                        }
                        String uNameString = uName.getText().toString().trim();
                        uNameString += " (My Number)";
                        dataBaseHelper.addOneContact(uNameString, uNumber.getText().toString());
                        dataBaseHelper.addOneMessage(uNumber.getText().toString(), "This is a test Message!", strDate, "", "","");

                        //refill the contact Array lists so that the Contact ID will be filled with the new information
                        storeDBtoArrays();
                        //Redisplay the list
                        inboxListAdapter.notifyDataSetChanged();
                    }
                }
            });

            builder.show();
        }

    }

    // This function is used to update the inboxlist whenever the DB changes.
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void storeDBtoArrays(){
        Cursor cursor = dataBaseHelper.readAllDataMessagesTable();
        EncryptionProcessor encryptionProcessor = new EncryptionProcessor();
        Cursor num;
        if(cursor.getCount() == 0){
            //do nothing
        }else{
            int i = 0;
            while (cursor.moveToNext()){
                messageID.add(cursor.getString(0));     //ID
                messageNum.add(cursor.getString(1));    //Number

                // Get Name from Contact Table
                // num is a cursor for contact table. need to put movetoFirst() since first index
                // of a cursor is always -1, giving us errors
                num = dataBaseHelper.readContactName(cursor.getString(1));
                if(num.moveToFirst() && cursor.getCount() == 1) {
                    do {
                        messageNames.add(num.getString(0));
                    } while (num.moveToNext());
                }else{
                    try {
                        messageNames.add(num.getString(0));
                    }catch (Exception e){
                        messageNames.add("Unknown");
                    }
                }
                num.close();

                if(cursor.getString(2).equals("URGENT BEACON SIGNAL SENT!") || cursor.getString(2).equals("URGENT BEACON SIGNAL RECEIVED!") || cursor.getString(1).equals(getUserSID())) {
                    messageText.add(cursor.getString(2));    //Message
                }else{
                    // Decrypt Text and send to array
                    byte[] encryptedData = Base64.decode(cursor.getString(2), Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE | Base64.NO_CLOSE);
                    //USE THE PUBLICKEY VARIABLE FROM THE MESSAGE
                    encryptionProcessor.receivingEncryptionProcessor(encryptedData, new BigInteger(cursor.getString(5)), Integer.parseInt(cursor.getString(6)));
                    Log.d("DHKeys", "Public Key from Database : " + cursor.getString(5));
                    Log.d("DHKeys", "Private Key from Database : " + cursor.getString(6));
                    String decodedData = encryptionProcessor.getDecodedText();
                    //Store part 2
                    messageText.add(decodedData.substring(9,decodedData.length()));    //Message
                }
                messageReceived.add(cursor.getString(3));    //Date and Time Received
                messageSent.add(cursor.getString(4));    //Date and Time Sent
                i++;
            }

        }
        cursor.close();
    }

    //ON ITEM CLICK FROM RECYCLER VIEW
    @Override
    public void onItemClick(View view, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_inboxmessage, getActivity().findViewById(android.R.id.content), false);
        dialog_name = viewInflated.findViewById(R.id.dialog_mname);
        dialog_num = viewInflated.findViewById(R.id.dialog_mnumber);
        dialog_mess = viewInflated.findViewById(R.id.dialog_message);
        dialog_date = viewInflated.findViewById(R.id.dialog_mdate);
        // Set up the text
        dialog_name.setText(messageNames.get(position));
        dialog_num.setText(messageNum.get(position));
        dialog_mess.setText(messageText.get(position));
        if(messageSent.get(position).equals("-")) dialog_date.setText(messageReceived.get(position));
        else dialog_date.setText(messageSent.get(position));

        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        builder.setView(viewInflated);

        builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                dataBaseHelper.deleteOneMessage(messageID.get(position));
                messageNames.remove(position);
                messageText.remove(position);
                messageNum.remove(position);
                messageReceived.remove(position);
                messageSent.remove(position);
                inboxListAdapter.notifyDataSetChanged();
            }
        });
        builder.setPositiveButton("Back", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }


    String getDecryptionKey(String number){
        String SID = null;
        Cursor cursor;
        cursor = dataBaseHelper.readContactKey(number);

        if (cursor.getCount() == 0) {
            Toast.makeText(context, "No User SID!", Toast.LENGTH_SHORT).show();
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

    static String getUserSID(){
        String SID = null;
        Cursor cursor;
        cursor = dataBaseHelper.readUserSID();

        if (cursor.getCount() == 0) {
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

}