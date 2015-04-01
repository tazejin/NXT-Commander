package com.jurajpaulik.nxtcommander;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

// Trieda sluzi na komunikaciu medzi NXT robotom a mobilom.
// Komunikacia prebieha cez LCP (Lego communaction protocol)

public class BTKomunikacia extends Thread {
    public static final int MOTOR_A = 0;
    public static final int MOTOR_B = 1;
    public static final int MOTOR_C = 2;
    public static final int MOTOR_RESET = 10;
    public static final int DO_BEEP = 51;
    public static final int READ_MOTOR_STATE = 60;
    public static final int GET_FIRMWARE_VERSION = 70;
    public static final int CLOSE = 99;
    public static final int GET_BATTERY_STATE = 100;
    public static final int GET_DEVICE_INFO = 101;
    public static final int WAIT = 102;

    public static final int SHOW_MESSAGE = 1000;
    public static final int STATE_CONNECTED = 1001;
    public static final int STATE_ERROR_CONNECTING = 1002;
    public static final int STATE_ERROR_PAIRING = 1022;
    public static final int STATE_MOTOR = 1003;
    public static final int STATE_ERROR_RECIEVER = 1004;
    public static final int STATE_ERROR_SENDING = 1005;
    public static final int FIRMWARE_VERSION = 1006;
    public static final int FIND_FILES = 1007;
    public static final int START_PROGRAM = 1008;
    public static final int STOP_PROGRAM = 1009;
    public static final int GET_PROGRAM_NAME = 1010;
    public static final int PROGRAM_NAME = 1011;
    public static final int BATTERY_INFO = 1012;
    public static final int NXT_INFO = 1013;

    public static final int NO_DELAY = 0;
    public static final int SHORT_DELAY = 1;

    // pripojenie na lego pomocou specialnej "adresy", ktoru maju registrovanu
    private static final UUID SERIAL_PORT_SERVICE_CLASS_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // OUI registrovane LEGOM
    public static final String OUI_LEGO = "00:16:53";

    private Resources mResources;
    private BluetoothAdapter btAdapter;
    private BluetoothSocket nxtBTsocket = null;
    private OutputStream nxtOutputStream = null;
    private InputStream nxtInputStream = null;
    private boolean connected = false;

    private Handler uiHandler;
    private String mMACaddress;
    private BTPripojenie myOwner;

    private byte[] returnMessage;

    public BTKomunikacia(BTPripojenie myOwner, Handler uiHandler, BluetoothAdapter btAdapter, Resources resources) {
        this.myOwner = myOwner;
        this.uiHandler = uiHandler;
        this.btAdapter = btAdapter;
        this.mResources = resources;
    }

    // handler pre pripojenie
    public Handler getHandler() {
        return myHandler;
    }

    // vracianie spravy spat z nxt robota
    public byte[] getReturnMessage() {
        return returnMessage;
    }

    // metoda na nadstavenie MACAddressy
    public void setMACAddress(String mMACaddress) {
        this.mMACaddress = mMACaddress;
    }

    // vratenie stavu pripojenia
    public boolean isConnected() {
        return connected;
    }

    // Pri spusteni vytvori spojenie, caka na prichadzajuce spravy a odosiela dalej na vybavenie.
    // Thread je zniceny, ak zatvorime/prerusime spojenie s robotom
    @Override
    public void run() {
        try {        
            createNXTconnection();
        }
        catch (IOException ignored) {}
        while (connected) {
            try {
                returnMessage = receiveMessage();
                if ((returnMessage.length >= 2) && ((returnMessage[0] == LCPSpravy.REPLY) ||
                    (returnMessage[0] == LCPSpravy.DIRECT_COMMAND_NOREPLY)))
                    dispatchMessage(returnMessage);
            } catch (IOException e) {
                // Ak uz je spojenie prerusene, neposielame oznamenie
                if (connected)
                    sendState(STATE_ERROR_RECIEVER);
                return;
            }
        }
    }

    // Vytvorenie bluetooth spojenia s SerialPortServiceClass_UUID
    // Pri chybe posiela tato metoda spravu pouzivatelovi
    // alebo spravi vynimku v pripade, ze chyba nieje nijak spracovana
    public void createNXTconnection() throws IOException {
        try {
            BluetoothSocket nxtBTSocketTemporary;
            BluetoothDevice nxtDevice;
            nxtDevice = btAdapter.getRemoteDevice(mMACaddress);
            // vytvorenie spojenia a nasledne pripojenie, pri chybe parovania vypise spravu
            nxtBTSocketTemporary = nxtDevice.createRfcommSocketToServiceRecord(SERIAL_PORT_SERVICE_CLASS_UUID);
            try {
                nxtBTSocketTemporary.connect();
            }
            catch (IOException e) {  
                if (myOwner.isPairing()) {
                    if (uiHandler != null) {
                        sendState(STATE_ERROR_PAIRING);
                    }
                    else
                        throw e;
                    return;
                }
                // Ak nefunguje metoda vysie, tak tato funguje pri urcitych mobilnych zariadeniach
                // konkretne na HTC Desire
                try {
                    Method mMethod = nxtDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                    nxtBTSocketTemporary = (BluetoothSocket) mMethod.invoke(nxtDevice, 1);
                    nxtBTSocketTemporary.connect();
                }
                catch (Exception e1){
                    if (uiHandler == null)
                        throw new IOException();
                    else
                        sendState(STATE_ERROR_CONNECTING);
                    return;
                }
            }
            nxtBTsocket = nxtBTSocketTemporary;
            nxtInputStream = nxtBTsocket.getInputStream();
            nxtOutputStream = nxtBTsocket.getOutputStream();
            connected = true;
        } catch (IOException e) {
            if (uiHandler == null)
                throw e;
            else {
                if (myOwner.isPairing())
                sendState(STATE_ERROR_CONNECTING);
                return;
            }
        }
        // Ak bolo vsetko v poriadnu, posleme satus o pripojeni
        if (uiHandler != null)
            sendState(STATE_CONNECTED);
    }

    // Zatvorenie bluetooth pripojenia. Ak nastane chyba tak sprava je poslana pouzivatelovi
    // alebo sa vytvori vynimka v pripade ziadnej spravy
    public void destroyNXTconnection() throws IOException {
        try {
            if (nxtBTsocket != null) {
                connected = false;
                nxtBTsocket.close();
                nxtBTsocket = null;
            }
            nxtInputStream = null;
            nxtOutputStream = null;
        } catch (IOException e) {
            if (uiHandler == null)
                throw e;
            else
                sendToast(mResources.getString(R.string.problem_at_closing));
        }
    }

    // Posielanie spravy na otvorenom OutputStreame
    // spravu posielame ako pole bajtov
    public void sendMessage(byte[] message) throws IOException {
        if (nxtOutputStream == null)
            throw new IOException();

        // posiela dlzku spravy
        int messageLength = message.length;
        nxtOutputStream.write(messageLength);
        nxtOutputStream.write(messageLength >> 8);
        nxtOutputStream.write(message, 0, message.length);
    }

    // Prijimanie spravy na otvorenom InputStreame
    // spravu nasledne ulozime
    public byte[] receiveMessage() throws IOException {
        if (nxtInputStream == null)
            throw new IOException();

        int length = nxtInputStream.read();
        length = (nxtInputStream.read() << 8) + length;
        byte[] returnMessage = new byte[length];
        nxtInputStream.read(returnMessage);
        return returnMessage;
    }

    // Posielanie spravy na otvorenom OutputStreame
    // spravu posielame ako pole bajtov
    // v pripade chyby vratime spravu chyby
    private void sendMessageAndState(byte[] message) {
        if (nxtOutputStream == null)
            return;
        try {
            sendMessage(message);
        }
        catch (IOException e) {
            sendState(STATE_ERROR_SENDING);
        }
    }

    // Odovzdavanie sprav na spracovanie
    // prijatu spravu porovnavame s preddefinovanymi z LEGO prirucky
    private void dispatchMessage(byte[] message) {
        switch (message[1]) {
            case LCPSpravy.GET_OUTPUT_STATE:
                if (message.length >= 25)
                    sendState(STATE_MOTOR);
                break;
            case LCPSpravy.GET_FIRMWARE_VERSION:
                if (message.length >= 7)
                    sendState(FIRMWARE_VERSION);
                break;
            case LCPSpravy.FIND_FIRST:
            case LCPSpravy.FIND_NEXT:
                if (message.length >= 28) {
                    if (message[2] == 0)
                        sendState(FIND_FILES);
                }
                break;
            case LCPSpravy.GET_CURRENT_PROGRAM_NAME:
                if (message.length >= 23) {
                    sendState(PROGRAM_NAME);
                }
                break;
        }
    }

    // metoda na pipnutie robota, musime zadat frekvenciu a dlzku
    // nasledne posleme spravu na cakanie, aby sme nemohli spamovat a zaplnit stream
    private void doBeep(int frequency, int duration) {
        byte[] message = LCPSpravy.getBeepMessage(frequency, duration);
        sendMessageAndState(message);
        waitSomeTime(20);
    }

    // spustenie programu v robotoci, posielame meno programu ako parameter
    private void startProgram(String programName) {
        byte[] message = LCPSpravy.getStartProgramMessage(programName);
        sendMessageAndState(message);
    }

    // metoda na ziskanie mena programu
    private void getProgramName() {
        byte[] message = LCPSpravy.getProgramNameMessage();
        sendMessageAndState(message);
    }

    // zmena rychlosti motoru
    private void changeMotorSpeed(int motor, int speed) {
        if (speed > 100)
            speed = 100;
        else if (speed < -100)
            speed = -100;
        byte[] message = LCPSpravy.getMotorMessage(motor, speed);
        sendMessageAndState(message);
    }

    // vyresetovanie motora
    private void reset(int motor) {
        byte[] message = LCPSpravy.getResetMessage(motor);
        sendMessageAndState(message);
    }

    // zistenie stavu motora
    private void readMotorState(int motor) {
        byte[] message = LCPSpravy.getOutputStateMessage(motor);
        sendMessageAndState(message);
    }

    // zistenie verzie firmware v robotovi
    private void getFirmwareVersion() {
        byte[] message = LCPSpravy.getFirmwareVersionMessage();
        sendMessageAndState(message);
    }

    // metoda na najdene vsetkych suborov zo vsetkymi priponami v robotovi
    // pouzivame "divoke karty" *.*
    private void findFiles(boolean findFirst, int handle) {
        byte[] message = LCPSpravy.getFindFilesMessage(findFirst, handle, "*.*");
        sendMessageAndState(message);
    }

    // zistenie stavu baterky
    private void getBatteryLevel(){
        byte[] message = LCPSpravy.getBatteryLevelMessage();
        sendMessageAndState(message);
    }

    // zistenie informacii o NXT
    private void getDeviceInfo(){
        byte[] message = LCPSpravy.getDeviceInfoMessage();
        sendMessageAndState(message);
    }

    // metoda na pockanie urciteho casu
    private void waitSomeTime(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    // poslanie spravy do mobilneho zariadenia vo forme TOASTu
    private void sendToast(String toastText) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", SHOW_MESSAGE);
        myBundle.putString("toastText", toastText);
        sendBundle(myBundle);
    }

    // poslanie statusu
    private void sendState(int message) {
        Bundle myBundle = new Bundle();
        myBundle.putInt("message", message);
        sendBundle(myBundle);
    }

    // poslanie viacej sprav naraz
    private void sendBundle(Bundle myBundle) {
        Message myMessage = myHandler.obtainMessage();
        myMessage.setData(myBundle);
        uiHandler.sendMessage(myMessage);
    }

    // prijimanie sprav z UI
    // sluzi na ziskavanie sprav z hlavnej triedy a potom vykona metodu, ktoru ma
    // parametre ziskava z hlavnej triedy (metody posielame s parametrom)
    final Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message myMessage) {
            int message;
            switch (message = myMessage.getData().getInt("message")) {
                case MOTOR_A:
                case MOTOR_B:
                case MOTOR_C:
                    changeMotorSpeed(message, myMessage.getData().getInt("value1"));
                    break;
                case MOTOR_RESET:
                    reset(myMessage.getData().getInt("value1"));
                    break;
                case START_PROGRAM:
                    startProgram(myMessage.getData().getString("name"));
                    break;
                case GET_PROGRAM_NAME:
                    getProgramName();
                    break;    
                case DO_BEEP:
                    doBeep(myMessage.getData().getInt("value1"), myMessage.getData().getInt("value2"));
                    break;
                case READ_MOTOR_STATE:
                    readMotorState(myMessage.getData().getInt("value1"));
                    break;
                case GET_FIRMWARE_VERSION:
                    getFirmwareVersion();
                    break;
                case FIND_FILES:
                    findFiles(myMessage.getData().getInt("value1") == 0, myMessage.getData().getInt("value2"));
                    break;
                case CLOSE:
                    // odoslanie spravy na zastavenie motorov pred ukoncenim spojenia
                    changeMotorSpeed(MOTOR_A, 0);
                    changeMotorSpeed(MOTOR_B, 0);
                    changeMotorSpeed(MOTOR_C, 0);
                    waitSomeTime(500);
                    try {
                        destroyNXTconnection();
                    }
                    catch (IOException ignored) { }
                    break;
                case GET_BATTERY_STATE:
                    getBatteryLevel();
                    break;
                case GET_DEVICE_INFO:
                    getDeviceInfo();
                    break;
                case WAIT:
                    waitSomeTime(myMessage.getData().getInt("value1"));
                    break;
            }
        }
    };
}
