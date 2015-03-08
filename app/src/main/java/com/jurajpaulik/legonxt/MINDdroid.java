package com.jurajpaulik.legonxt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

/**
 * This class is for talking to a LEGO NXT robot and controlling it
 * via bluetooth and the built in acceleration sensor.
 * The communciation to the robot is done via LCP (LEGO communication protocol), 
 * so no special software has to be installed on the robot.
 */
public class MINDdroid extends Activity implements BTConnectable {

    public static final int UPDATE_TIME = 200;
    public static final int MENU_TOGGLE_CONNECT = Menu.FIRST;
    public static final int MENU_START_SW = Menu.FIRST + 1;
    public static final int MENU_START_SOUND = Menu.FIRST + 2;
    public static final int MENU_QUIT = Menu.FIRST + 3;

    public static final int ACTION_BUTTON_SHORT = 0;
    public static final int ACTION_BUTTON_LONG = 1;

    public static final int REQUEST_CONNECT_DEVICE = 1000;
    public static final int REQUEST_ENABLE_BT = 2000;
    public BTCommunicator myBTCommunicator = null;
    public boolean connected = false;
    public ProgressDialog connectingProgressDialog;
    public Handler btcHandler;
    public Menu myMenu;
    public Activity thisActivity;
    public boolean btErrorPending = false;
    public boolean pairing;
    public static boolean btOnByUs = false;
    int mRobotType;
    int motorLeft;
    public int directionLeft; // +/- 1
    int motorRight;
    public boolean stopAlreadySent = false;
    public int directionRight; // +/- 1
    public int motorAction;
    public int directionAction; // +/- 1
    public List<String> programList;
    public List<String> soundList;
    public static final int MAX_PROGRAMS = 20;
    public String programToStart;
    public Toast reusableToast;
    public int power = 80;
    public boolean command1;
    public boolean command2;
    public boolean command3;
    public boolean command4;
    public boolean command5;
    public int buttonID;
    public double leftMotor;
    public double rightMotor;
    public int param;
    public int param1;
    public int param2;
    public int param3;
    public int param4;
    public int param5;

    /**
     * Asks if bluetooth was switched on during the runtime of the app. For saving 
     * battery we switch it off when the app is terminated.
     * @return true, when bluetooth was switched on by the app
     */
    public static boolean isBtOnByUs() {
        return btOnByUs;
    }

    /**
     * Sets a flag when bluetooth was switched on durin runtime
     * @param btOnByUs true, when bluetooth was switched on by the app
     */
    public static void setBtOnByUs(boolean btOnByUs) {
        MINDdroid.btOnByUs = btOnByUs;
    }

    /**
     * @return true, when currently pairing 
     */
    @Override
    public boolean isPairing() {
        return pairing;
    }

    /**
     * Called when the activity is first created. Inititializes all the
     * graphical views.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        mRobotType = this.getIntent().getIntExtra(SplashMenu.MINDDROID_ROBOT_TYPE,
                R.id.robot_type_shooterbot);
        setUpByType();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // setup our view, give it focus and display.
        setContentView(R.layout.ovladanie);
        reusableToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        ImageButton buttonUp = (ImageButton) findViewById(R.id.buttonUp);
        buttonUp.setOnTouchListener(new DirectionButtonOnTouchListener(1, 1));
        ImageButton buttonLeft = (ImageButton) findViewById(R.id.buttonLeft);
        buttonLeft.setOnTouchListener(new DirectionButtonOnTouchListener(0.6, -0.6));
        ImageButton buttonDown = (ImageButton) findViewById(R.id.buttonDown);
        buttonDown.setOnTouchListener(new DirectionButtonOnTouchListener(-1, -1));
        ImageButton buttonRight = (ImageButton) findViewById(R.id.buttonRight);
        buttonRight.setOnTouchListener(new DirectionButtonOnTouchListener(-0.6, 0.6));

        final SeekBar powerSeekBar = (SeekBar) findViewById(R.id.power_seekbar);
        powerSeekBar.setProgress(power);
        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                if (powerSeekBar.getProgress() < 25){
                    powerSeekBar.setProgress(25);
                }
                power = powerSeekBar.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Initialization of the motor commands for the different robot types.
     */
    public void setUpByType() {
        switch (mRobotType) {
            default:
                // default
                motorLeft = BTCommunicator.MOTOR_B;
                directionLeft = 1;
                motorRight = BTCommunicator.MOTOR_C;
                directionRight = 1;
                break;
        }
    }

    /**
     * Updates the menus and possible buttons when connection status changed.
     */
    public void updateButtonsAndMenu() {

        if (myMenu == null)
            return;

        myMenu.removeItem(MENU_TOGGLE_CONNECT);

        if (connected) {
            myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.disconnect)).setIcon(R.drawable.ic_menu_connected);

        } else {
            myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.connect)).setIcon(R.drawable.ic_menu_connect);
        }

    }

    /**
     * Creates a new object for communication to the NXT robot via bluetooth and fetches the corresponding handler.
     */
    public void createBTCommunicator() {
        // interestingly BT adapter needs to be obtained by the UI thread - so we pass it in in the constructor
        myBTCommunicator = new BTCommunicator(this, myHandler, BluetoothAdapter.getDefaultAdapter(), getResources());
        btcHandler = myBTCommunicator.getHandler();
    }

    /**
     * Creates and starts the a thread for communication via bluetooth to the NXT robot.
     * @param mac_address The MAC address of the NXT robot.
     */
    public void startBTCommunicator(String mac_address) {
        connected = false;
        connectingProgressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.connecting_please_wait), true);

        if (myBTCommunicator != null) {
            try {
                myBTCommunicator.destroyNXTconnection();
            }
            catch (IOException e) { }
        }
        createBTCommunicator();
        myBTCommunicator.setMACAddress(mac_address);
        myBTCommunicator.start();
        updateButtonsAndMenu();
    }

    /**
     * Sends a message for disconnecting to the communcation thread.
     */
    public void destroyBTCommunicator() {

        if (myBTCommunicator != null) {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.DISCONNECT, 0, 0);
            myBTCommunicator = null;
        }

        connected = false;
        updateButtonsAndMenu();
    }

    /**
     * Gets the current connection status.
     * @return the current connection status to the robot.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Starts a program on the NXT robot.
     */
    public void startProgram(String name) {
        // for .rxe programs: get program name, eventually stop this and start the new one delayed
        // is handled in startRXEprogram()
        if (name.endsWith(".rxe")) {
            programToStart = name;
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.GET_PROGRAM_NAME, 0, 0);
            return;
        }

        // for all other programs: just start the program (.rpg are files created with NXT PROGRAM menu,
        // .rso are sound files found in brick)
        sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.START_PROGRAM, name);
    }

    /**
     * Depending on the status (whether the program runs already) we stop it, wait and restart it again.
     * @param status The current status, 0x00 means that the program is already running.
     */
    public void startRXEprogram(byte status) {
        if (status == 0x00) {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.STOP_PROGRAM, 0, 0);
            sendBTCmessage(1000, BTCommunicator.START_PROGRAM, programToStart);
        }
        else {
            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.START_PROGRAM, programToStart);
        }
    }

    public class DirectionButtonOnTouchListener implements View.OnTouchListener {
        public double leftMotor;
        public double rightMotor;

        public DirectionButtonOnTouchListener(double l, double r) {
            leftMotor = l;
            rightMotor = r;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                updateMotorControl(leftMotor * power, rightMotor * power);
            } else if ((action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL)) {
                updateMotorControl(0, 0);
            }
            return true;
        }
    }

    /**
     * Sends the motor control values to the communcation thread.
     * @param left The power of the left motor from 0 to 100.
     * @param right The power of the right motor from 0 to 100.
     */
    public void updateMotorControl(double left, double right) {

        if (myBTCommunicator != null) {
            // don't send motor stop twice
            if ((left == 0) && (right == 0)) {
                if (stopAlreadySent)
                    return;
                else
                    stopAlreadySent = true;
            }
            else
                stopAlreadySent = false;

            // send messages via the handler
            sendBTCmessage(BTCommunicator.NO_DELAY, motorLeft, (int) (left * directionLeft), 0);
            sendBTCmessage(BTCommunicator.NO_DELAY, motorRight, (int) (right * directionRight), 0);
        }
    }

    /**
     * Sends the motor control values to the communcation thread.
     * @param left The power of the left motor from 0 to 100.
     * @param right The power of the right motor from 0 to 100.
     */
    public void updateMotorControlTime(double left, double right, int duration) {

        if (myBTCommunicator != null) {
            // don't send motor stop twice
            if ((left == 0) && (right == 0)) {
                if (stopAlreadySent)
                    return;
                else
                    stopAlreadySent = true;
            }
            else
                stopAlreadySent = false;

            // send messages via the handler
            sendBTCmessage(BTCommunicator.NO_DELAY, motorLeft, (int) (left * directionLeft), duration);
            sendBTCmessage(BTCommunicator.NO_DELAY, motorRight, (int) (right * directionRight), duration);
        }
    }

    /**
     * Sends the message via the BTCommuncator to the robot.
     * @param delay time to wait before sending the message.
     * @param message the message type (as defined in BTCommucator)
     * @param value1 first parameter
     * @param value2 second parameter
     */
    void sendBTCmessage(int delay, int message, int value1, int value2) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        myBundle.putInt("value1", value1);
        myBundle.putInt("value2", value2);
        Message myMessage = myHandler.obtainMessage();
        myMessage.setData(myBundle);

        if (delay == 0)
            btcHandler.sendMessage(myMessage);

        else
            btcHandler.sendMessageDelayed(myMessage, delay);
    }

    /**
     * Sends the message via the BTCommuncator to the robot.
     * @param delay time to wait before sending the message.
     * @param message the message type (as defined in BTCommucator)
     */
    void sendBTCmessage(int delay, int message, String name) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        myBundle.putString("name", name);
        Message myMessage = myHandler.obtainMessage();
        myMessage.setData(myBundle);

        if (delay == 0)
            btcHandler.sendMessage(myMessage);
        else
            btcHandler.sendMessageDelayed(myMessage, delay);
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            //mView.registerListener();
        }
        catch (IndexOutOfBoundsException ex) {
            showToast(R.string.sensor_initialization_failure, Toast.LENGTH_LONG);
            destroyBTCommunicator();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // no bluetooth available
        if (BluetoothAdapter.getDefaultAdapter()==null) {
            showToast(R.string.bt_initialization_failure, Toast.LENGTH_LONG);
            destroyBTCommunicator();
            finish();
            return;
        }

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            selectNXT();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyBTCommunicator();
    }

    @Override
    public void onPause() {
        //  mView.unregisterListener();
        destroyBTCommunicator();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // mView.unregisterListener();
    }

    /**
     * Creates the menu items
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myMenu = menu;
        myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.connect));
        myMenu.add(0, MENU_START_SW, 2, getResources().getString(R.string.start));
        myMenu.add(0, MENU_START_SOUND, 3, getResources().getString(R.string.sound));
        myMenu.add(0, MENU_QUIT, 4, getResources().getString(R.string.quit));
        updateButtonsAndMenu();
        return true;
    }

    /**
     * Enables/disables the menu items
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean displayMenu;
        displayMenu = super.onPrepareOptionsMenu(menu);
        if (displayMenu) {
            boolean startEnabled = false;
            if (myBTCommunicator != null)
                startEnabled = myBTCommunicator.isConnected();
            menu.findItem(MENU_START_SW).setEnabled(startEnabled);
            menu.findItem(MENU_START_SOUND).setEnabled(startEnabled);
        }
        return displayMenu;
    }

    /**
     * Handles item selections
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_TOGGLE_CONNECT:

                if (myBTCommunicator == null || connected == false) {
                    selectNXT();

                } else {
                    destroyBTCommunicator();
                    updateButtonsAndMenu();
                }

                return true;

            case MENU_START_SW:
                if (programList.size() == 0) {
                    showToast(R.string.no_programs_found, Toast.LENGTH_SHORT);
                    break;
                }

                FileDialog myFileDialog = new FileDialog(this, programList);
                myFileDialog.show(mRobotType == R.id.robot_type);
                return true;

            case MENU_START_SOUND:
                if (soundList.size() == 0) {
                    showToast(R.string.no_programs_found, Toast.LENGTH_SHORT);
                    break;
                }

                FileDialog mySounds = new FileDialog(this, soundList);
                mySounds.show(mRobotType == R.id.robot_type_lejos);
                return true;

            case MENU_QUIT:
                destroyBTCommunicator();
                finish();

                if (btOnByUs)
                    showToast(R.string.bt_off_message, Toast.LENGTH_SHORT);

                SplashMenu.quitApplication();
                return true;
        }

        return false;
    }

    /**
     * Displays a message as a toast
     * @param textToShow the message
     * @param length the length of the toast to display
     */
    public void showToast(String textToShow, int length) {
        reusableToast.setText(textToShow);
        reusableToast.setDuration(length);
        reusableToast.show();
    }

    /**
     * Displays a message as a toast
     * @param resID the ressource ID to display
     * @param length the length of the toast to display
     */
    public void showToast(int resID, int length) {
        reusableToast.setText(resID);
        reusableToast.setDuration(length);
        reusableToast.show();
    }

    /**
     * Receive messages from the BTCommunicator
     */
    final Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message myMessage) {
            switch (myMessage.getData().getInt("message")) {
                case BTCommunicator.DISPLAY_TOAST:
                    showToast(myMessage.getData().getString("toastText"), Toast.LENGTH_SHORT);
                    break;
                case BTCommunicator.STATE_CONNECTED:
                    connected = true;
                    programList = new ArrayList<String>();
                    soundList = new ArrayList<String>();
                    connectingProgressDialog.dismiss();
                    updateButtonsAndMenu();
                    sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.GET_FIRMWARE_VERSION, 0, 0);
                    sendBTCmessage(BTCommunicator.SHORT_DELAY, BTCommunicator.GET_BATTERY_LEVEL, 0, 0);
                    break;
                case BTCommunicator.MOTOR_STATE:

                    if (myBTCommunicator != null) {
                        byte[] motorMessage = myBTCommunicator.getReturnMessage();
                        int position = byteToInt(motorMessage[21]) + (byteToInt(motorMessage[22]) << 8) + (byteToInt(motorMessage[23]) << 16)
                                + (byteToInt(motorMessage[24]) << 24);
                    }

                    break;

                case BTCommunicator.STATE_CONNECTERROR_PAIRING:
                    connectingProgressDialog.dismiss();
                    destroyBTCommunicator();
                    break;

                case BTCommunicator.STATE_CONNECTERROR:
                    connectingProgressDialog.dismiss();
                case BTCommunicator.STATE_RECEIVEERROR:
                case BTCommunicator.STATE_SENDERROR:

                    destroyBTCommunicator();
                    if (btErrorPending == false) {
                        btErrorPending = true;
                        // inform the user of the error with an AlertDialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(thisActivity);
                        builder.setTitle(getResources().getString(R.string.bt_error_dialog_title))
                                .setMessage(getResources().getString(R.string.bt_error_dialog_message)).setCancelable(false)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        btErrorPending = false;
                                        dialog.cancel();
                                        selectNXT();
                                    }
                                });
                        builder.create().show();
                    }

                    break;

                case BTCommunicator.FIRMWARE_VERSION:

                    if (myBTCommunicator != null) {
                        byte[] firmwareMessage = myBTCommunicator.getReturnMessage();
                        // check if we know the firmware
                        boolean isLejosMindDroid = true;
                        for (int pos=0; pos<4; pos++) {
                            if (firmwareMessage[pos + 3] != LCPMessage.FIRMWARE_VERSION_LEJOSMINDDROID[pos]) {
                                isLejosMindDroid = false;
                                break;
                            }
                        }
                        if (isLejosMindDroid) {
                            mRobotType = R.id.robot_type_lejos;
                            setUpByType();
                        }
                        // afterwards we search for all files on the robot
                        sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.FIND_FILES, 0, 0);
                    }

                    break;

                case BTCommunicator.GET_DEVICE_INFO:
                    byte[] deviceMessage = myBTCommunicator.getReturnMessage();
                    String deviceStr = new String(deviceMessage);
                    deviceStr = deviceStr.replaceAll("\0", "");
                    Log.e("Device info: ", deviceStr);
                    break;
                case BTCommunicator.FIND_FILES:

                    if (myBTCommunicator != null) {
                        byte[] fileMessage = myBTCommunicator.getReturnMessage();
                        String fileName = new String(fileMessage, 4, 20);
                        fileName = fileName.replaceAll("\0","");

                        if (mRobotType == R.id.robot_type_lejos || fileName.endsWith(".rxe") || fileName.endsWith(".rpg")) {
                            programList.add(fileName);
                        }

                        if (fileName.endsWith(".rso")) {
                            soundList.add(fileName);
                        }

                        // find next entry with appropriate handle,
                        // limit number of programs (in case of error (endless loop))
                        if (programList.size() <= MAX_PROGRAMS)
                            sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.FIND_FILES,
                                    1, byteToInt(fileMessage[3]));
                    }

                    break;

                case BTCommunicator.PROGRAM_NAME:
                    if (myBTCommunicator != null) {
                        byte[] returnMessage = myBTCommunicator.getReturnMessage();
                        startRXEprogram(returnMessage[2]);
                    }

                    break;

                case BTCommunicator.VIBRATE_PHONE:
                    if (myBTCommunicator != null) {
                        byte[] vibrateMessage = myBTCommunicator.getReturnMessage();
                        Vibrator myVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        myVibrator.vibrate(vibrateMessage[2]*10);
                    }

                    break;
                case BTCommunicator.GET_BATTERY_LEVEL:
                    if (myBTCommunicator != null){
                        byte[] batteryMessage = myBTCommunicator.getReturnMessage();
                        String batteryStr = new String(batteryMessage);
                        Log.e("Battery level:", batteryStr);
                        break;
                    }
            }
        }
    };

    public int byteToInt(byte byteValue) {
        int intValue = (byteValue & (byte) 0x7f);

        if ((byteValue & (byte) 0x80) != 0)
            intValue |= 0x80;

        return intValue;
    }

    void selectNXT() {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address and start a new bt communicator thread
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    pairing = data.getExtras().getBoolean(DeviceListActivity.PAIRING);
                    startBTCommunicator(address);
                }

                break;

            case REQUEST_ENABLE_BT:

                // When the request to enable Bluetooth returns
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        btOnByUs = true;
                        selectNXT();
                        break;
                    case Activity.RESULT_CANCELED:
                        showToast(R.string.bt_needs_to_be_enabled, Toast.LENGTH_SHORT);
                        finish();
                        break;
                    default:
                        showToast(R.string.problem_at_connecting, Toast.LENGTH_SHORT);
                        finish();
                        break;
                }

                break;
        }
    }

    public void changeIcon1(View view){
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        registerForContextMenu(field1);
        this.openContextMenu(field1);
    }

    public void changeIcon2(View view){
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        registerForContextMenu(field2);
        this.openContextMenu(field2);
    }

    public void changeIcon3(View view){
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        registerForContextMenu(field3);
        this.openContextMenu(field3);
    }

    public void changeIcon4(View view){
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        registerForContextMenu(field4);
        this.openContextMenu(field4);
    }

    public void changeIcon5(View view){
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        registerForContextMenu(field5);
        this.openContextMenu(field5);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        MenuInflater inflater1 = getMenuInflater();
        MenuInflater inflater2 = getMenuInflater();
        buttonID = view.getId();

        if (buttonID==R.id.Field1){
            inflater1.inflate(R.menu.menu_programovanie, menu);
            command1 = true;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = false;
        } else if (buttonID==R.id.Field2){
            inflater2.inflate(R.menu.menu_programovanie2, menu);
            command1 = false;
            command2 = true;
            command3 = false;
            command4 = false;
            command5 = false;
        } else if (buttonID==R.id.Field3){
            inflater2.inflate(R.menu.menu_programovanie2, menu);
            command1 = false;
            command2 = false;
            command3 = true;
            command4 = false;
            command5 = false;
        } else if (buttonID==R.id.Field4){
            inflater2.inflate(R.menu.menu_programovanie2, menu);
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = true;
            command5 = false;
        } else if (buttonID==R.id.Field5){
            inflater2.inflate(R.menu.menu_programovanie2, menu);
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = true;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if(command1){
            switch (item.getItemId()) {
                case R.id.left:
                    field1.setImageResource(R.drawable.arrow_left);
                    field1.setContentDescription("1");
                    break;
                case R.id.right:
                    field1.setImageResource(R.drawable.arrow_right);
                    field1.setContentDescription("2");
                    break;
                case R.id.forward:
                    field1.setImageResource(R.drawable.arrow_up);
                    field1.setContentDescription("3");
                    break;
                case R.id.backward:
                    field1.setImageResource(R.drawable.arrow_down);
                    field1.setContentDescription("4");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        else if(command2){
            switch (item.getItemId()) {
                case R.id.left:
                    field2.setImageResource(R.drawable.arrow_left);
                    field2.setContentDescription("1");
                    break;
                case R.id.right:
                    field2.setImageResource(R.drawable.arrow_right);
                    field2.setContentDescription("2");
                    break;
                case R.id.forward:
                    field2.setImageResource(R.drawable.arrow_up);
                    field2.setContentDescription("3");
                    break;
                case R.id.backward:
                    field2.setImageResource(R.drawable.arrow_down);
                    field2.setContentDescription("4");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        else if(command3){
            switch (item.getItemId()) {
                case R.id.left:
                    field3.setImageResource(R.drawable.arrow_left);
                    field3.setContentDescription("1");
                    break;
                case R.id.right:
                    field3.setImageResource(R.drawable.arrow_right);
                    field3.setContentDescription("2");
                    break;
                case R.id.forward:
                    field3.setImageResource(R.drawable.arrow_up);
                    field3.setContentDescription("3");
                    break;
                case R.id.backward:
                    field3.setImageResource(R.drawable.arrow_down);
                    field3.setContentDescription("4");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        else if(command4){
            switch (item.getItemId()) {
                case R.id.left:
                    field4.setImageResource(R.drawable.arrow_left);
                    field4.setContentDescription("1");
                    break;
                case R.id.right:
                    field4.setImageResource(R.drawable.arrow_right);
                    field4.setContentDescription("2");
                    break;
                case R.id.forward:
                    field4.setImageResource(R.drawable.arrow_up);
                    field4.setContentDescription("3");
                    break;
                case R.id.backward:
                    field4.setImageResource(R.drawable.arrow_down);
                    field4.setContentDescription("4");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        else if(command5){
            switch (item.getItemId()) {
                case R.id.left:
                    field5.setImageResource(R.drawable.arrow_left);
                    field5.setContentDescription("1");
                    break;
                case R.id.right:
                    field5.setImageResource(R.drawable.arrow_right);
                    field5.setContentDescription("2");
                    break;
                case R.id.forward:
                    field5.setImageResource(R.drawable.arrow_up);
                    field5.setContentDescription("3");
                    break;
                case R.id.backward:
                    field5.setImageResource(R.drawable.arrow_down);
                    field5.setContentDescription("4");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        return true;
    }

    public void fieldMethod1(){
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        EditText parameter1 = (EditText) findViewById(R.id.par1);
        param1 = Integer.parseInt(String.valueOf(parameter1.getText()));
        if(field1.getContentDescription() == "1"){
            goLeftForward(param1);
        } else if (field1.getContentDescription() == "2"){
            goRightForward(param1);
        } else if (field1.getContentDescription() == "3"){
            goForward(param1);
        } else if (field1.getContentDescription() == "4"){
            goBackward(param1);
        }
    }

    public void fieldMethod2(){
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        EditText parameter2 = (EditText) findViewById(R.id.par2);
        param2 = Integer.parseInt(String.valueOf(parameter2.getText()));
        if(field2.getContentDescription() == "1"){
            goLeftForward(param2);
        } else if (field2.getContentDescription() == "2"){
            goRightForward(param2);
        } else if (field2.getContentDescription() == "3"){
            goForward(param2);
        } else if (field2.getContentDescription() == "4"){
            goBackward(param2);
        }
    }

    public void fieldMethod3(){
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        EditText parameter3 = (EditText) findViewById(R.id.par3);
        param3 = Integer.parseInt(String.valueOf(parameter3.getText()));
        if(field3.getContentDescription() == "1"){
            goLeftForward(param3);
        } else if (field3.getContentDescription() == "2"){
            goRightForward(param3);
        } else if (field3.getContentDescription() == "3"){
            goForward(param3);
        } else if (field3.getContentDescription() == "4"){
            goBackward(param3);
        }
    }

    public void fieldMethod4(){
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        EditText parameter4 = (EditText) findViewById(R.id.par4);
        param4 = Integer.parseInt(String.valueOf(parameter4.getText()));
        if(field4.getContentDescription() == "1"){
            goLeftForward(param4);
        } else if (field4.getContentDescription() == "2"){
            goRightForward(param4);
        } else if (field4.getContentDescription() == "3"){
            goForward(param4);
        } else if (field4.getContentDescription() == "4"){
            goBackward(param4);
        }
    }

    public void fieldMethod5(){
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        EditText parameter5 = (EditText) findViewById(R.id.par5);
        param5 = Integer.parseInt(String.valueOf(parameter5.getText()));
        if(field5.getContentDescription() == "1"){
            goLeftForward(param5);
        } else if (field5.getContentDescription() == "2"){
            goRightForward(param5);
        } else if (field5.getContentDescription() == "3"){
            goForward(param5);
        } else if (field5.getContentDescription() == "4"){
            goBackward(param5);
        }
    }

    public void goForward(int param){
        updateMotorControlTime(1 * power, 1 * power, param);
        new CountDownTimer(param * 1000 - 1000, 1) {

            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                updateMotorControl(0, 0);
            }
        }.start();
    }

    public void goBackward(int param){
        updateMotorControlTime(-1 * power, -1* power, param);
        new CountDownTimer(param * 1000 - 1000, 1){
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                updateMotorControl(0, 0);
            }
        }.start();
    }

    /*
    @param * 4 pre realne otocenie v °
    Ak je @param : 1440 tak otocenie je 360°
                    720 tak otocenie je 180°
                    360 tak otocenie je 90°
                    180 tak otocenie je 45°
    V stranach, ktore su urcene
     */
    public void goLeftForward(int param){
        //otocenie robota do lava, ziadne oneskorenie, pravy motor, param
        sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.MOTOR_C_ACTION, -180, 1);
    }

    public void goRightForward(int param){
        //otocenie robota do prava, ziadne oneskorenie, pravy motor, param
        sendBTCmessage(BTCommunicator.NO_DELAY, BTCommunicator.MOTOR_B_ACTION, 180, 1);
    }

    public void startProgramovanie(View view){
        fieldMethod1();
        new CountDownTimer(param1 * 1001 - 1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                fieldMethod2();
                new CountDownTimer(param2 * 1001 - 1000, 1){
                    public void onTick(long millisUntilFinished) {
                    }
                    public void onFinish() {
                        fieldMethod3();
                        new CountDownTimer(param3 * 1001 - 1000, 1){
                            public void onTick(long millisUntilFinished) {
                            }
                            public void onFinish() {
                                fieldMethod4();
                                new CountDownTimer(param4 * 1001 - 1000, 1){
                                    public void onTick(long millisUntilFinished) {
                                    }
                                    public void onFinish() {
                                        fieldMethod5();
                                    }
                                }.start();
                            }
                        }.start();
                    }
                }.start();
            }
        }.start();
    }

    public void switchProgramovanie(View view){
        setContentView(R.layout.programovanie);
    }
}