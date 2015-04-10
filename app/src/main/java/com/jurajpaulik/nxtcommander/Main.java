package com.jurajpaulik.nxtcommander;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Main extends Activity implements BTPripojenie{

    public static final int REQUEST_CONNECT_DEVICE = 1000;
    public static final int REQUEST_ENABLE_BT = 2000;
    public BTKomunikacia myBTKomunikacia = null;
    public boolean connected = false;
    public ProgressDialog connectingProgressDialog;
    public Handler btcHandler;
    public Activity thisActivity;
    public boolean btErrorPending = false;
    public boolean pairing;
    int motorLeft;
    public int directionLeft;
    int motorRight;
    public boolean stopAlreadySent = false;
    public int directionRight;
    public int directionAll;
    int motorAll;
    public List<String> programList;
    public List<String> soundList;
    public static final int MAX_PROGRAMS = 20;
    public Toast reusableToast;
    public int power = 100;
    public boolean command1;
    public boolean command2;
    public boolean command3;
    public boolean command4;
    public boolean command5;
    public boolean command6;
    public boolean command7;
    public int buttonID;
    public float currentMiliVolts;
    public String dotykovySenzor;
    public boolean dotyk = false;
    public int currentSoundL;
    public String zvukovySenzor;
    public int currentLightL;
    public String svetelnySenzor;
    public int currentUltrasonicL;
    public String ultrazvukovySenzor;
    public boolean svetloB = false;
    public boolean zvukB = false;
    public boolean splnenyDotyk = false;
    public boolean splnenyZvuk = false;
    public boolean splneneSvetlo = false;
    public boolean splnenyPohyb = false;
    public boolean splneneCakanie = false;
    public boolean splnenyUltraZ = false;
    public final long time1 = 500;
    public final long time3 = 2000;
    public final long time5 = 4000;
    public final long time10 = 9000;
    public long casovac1;
    public long casovac2;
    public long casovac3;
    public long casovac4;
    public long casovac5;
    public long casovac6;

    // metoda pri vytvoreni Options Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // naplnenie menu a pridanie poloziek do menu
        getMenuInflater().inflate(R.menu.menu_ovladanie, menu);
        return true;
    }

    // metoda pri kliknuti na polozky z menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // switch podla itemId
        switch (item.getItemId()) {
            // stlacenie pripojenia
            case R.id.connect:
                // ak uz nie sme pripojeny
                if (myBTKomunikacia == null || !connected) {
                    // tak vyhodime dialogove okno na pripojenie
                    selectNXT();
                }
                return true;
            // stlacenie odpojenia
            case R.id.disconnect:
                // "znicime" komunikator a teda prerusime spojenie
                destroyBTCommunicator();
                return true;
            // stlacenie ovladania
            case R.id.controls:
                // nastavenie layoutu na ovladanie
                setContentView(R.layout.ovladanie);
                // spustenie metody na nastavenei listenerov
                nastavenieListenerov();
                // korekcia posuvnika regulacie rychlosti
                napravaSeekBaru();
                // vypnutie toho "monitoringu" senzorov
                switchMonitor();
                return true;
            // stlacenie programovania
            case R.id.programming:
                // nastavenie layoutu na programovaci rezim
                setContentView(R.layout.programovanie);
                return true;
            // stlacenie programov
            case R.id.startSw:
                // ak mame prazdny zoznam
                if (programList.size() == 0) {
                    // vypiseme hlasku o nenajdeni programov v kocke
                    showToast(R.string.no_programs_found, Toast.LENGTH_SHORT);
                    break;
                }
                // "naplnenie" suborov z programlistu
                Subory mySubory = new Subory(this, programList);
                // vyhodenie dialogoveho okna
                mySubory.show();
                return true;
            // stlacenie zvukov
            case R.id.playSound:
                // ak mame prazdny zoznam
                if (soundList.size() == 0) {
                    // vypiseme hlasku o nenajdeni zvukov v kocke
                    showToast(R.string.no_sound_found, Toast.LENGTH_SHORT);
                    break;
                }
                // "naplnenie" zvukov z listu
                Subory mySounds = new Subory(this, soundList);
                // vyhodenie dialogoveho okna
                mySounds.show();
                return true;
        }
        return false;
    }

    // ak aplikacia sa momentalne paruje so zariadenim, vratime true
    @Override
    public boolean isPairing() {
        return pairing;
    }

    // metoda pri "vytvoreni" aplikacie
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        // nastavenie na nase hlavne okno - ovladanie
        setContentView(R.layout.ovladanie);
        super.onStart();
        // pripravenie toastu pre pouzitie
        reusableToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        // deklarovanie pola na ukazanie rychlosti
        TextView speedV = (TextView) findViewById(R.id.speedText);
        // a nasledovne nastavenie podla pola rychlosti
        speedV.setText(String.valueOf(power));

        // nastavenie motorov pri klasickom zapojeni
        motorLeft = BTKomunikacia.MOTOR_B;
        directionLeft = 1;
        motorRight = BTKomunikacia.MOTOR_C;
        directionRight = 1;
        motorAll = BTKomunikacia.MOTOR_ALL;
        directionAll = 1;

        // nastavenie listenerov
        nastavenieListenerov();
        // korekcia seek baru
        napravaSeekBaru();
        // switch
        switchMonitor();
    }

    public void napravaSeekBaru(){
        // deklarovanie SeekBaru na indikaciu "rychlosti"
        final SeekBar powerSeekBar = (SeekBar) findViewById(R.id.power_seekbar);
        // nastavenie hodnoty seekbaru podla rychlosti
        powerSeekBar.setProgress(power);
        // a najdenie rychlostneho pola ...
        TextView speedV = (TextView) findViewById(R.id.speedText);
        // a nastavenie retazca podla rychlosti
        speedV.setText(String.valueOf(power));

        // nastavenie / vytvorenie listenera na nasom seek bare
        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // metoda, kt. sleduje zmenu seekbaru
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                // ak sa snazi pouzivatel posunut seekbar na hodnotu mensiu ako 25
                // tak ho aj tak nepustime
                // je to hodnota, pri ktorej sa robot aspon trochu pohne
                if (powerSeekBar.getProgress() < 25){
                    powerSeekBar.setProgress(25);
                }
                // a nastavime pole power z hodnoty seekbaru
                power = powerSeekBar.getProgress();
                // a este to pole s rychlostou
                TextView speedV = (TextView) findViewById(R.id.speedText);
                speedV.setText(String.valueOf(power));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    public void nastavenieListenerov(){
        // nastavenie hodnot pre plynule ovladanie, podla parametrov sa generuju data posielane
        // do nxt kocky, ktora podla toho otaca motor
        // motor berie hodnotu od -100 do 100
        // hodnoty su uvedene nizsie, podrobnosti v metode na ovladanie
        // 0.5 pre polovicne otocenie, cize do prava alebo do lava (nasobia sa rychlostou)
        ImageButton buttonUp = (ImageButton) findViewById(R.id.buttonUp);
        buttonUp.setOnTouchListener(new DirectionButtonOnTouchListener(1, 1));
        ImageButton buttonLeft = (ImageButton) findViewById(R.id.buttonLeft);
        buttonLeft.setOnTouchListener(new DirectionButtonOnTouchListener(0.5, -0.5));
        ImageButton buttonDown = (ImageButton) findViewById(R.id.buttonDown);
        buttonDown.setOnTouchListener(new DirectionButtonOnTouchListener(-1, -1));
        ImageButton buttonRight = (ImageButton) findViewById(R.id.buttonRight);
        buttonRight.setOnTouchListener(new DirectionButtonOnTouchListener(-0.5, 0.5));
    }

    public void switchMonitor(){
        // deklarovanie vsetky textov
        Switch switchM = (Switch) findViewById(R.id.switchMonitoring);
        final TextView textBattery = (TextView) findViewById(R.id.textBattery);
        final TextView textSound = (TextView) findViewById(R.id.textSound);
        final TextView textTouch = (TextView) findViewById(R.id.textTouch);
        final TextView textLight = (TextView) findViewById(R.id.textLight);
        final TextView textUltra = (TextView) findViewById(R.id.textUltra);
        final TextView textLightSenzor = (TextView) findViewById(R.id.lightSenzor);
        final TextView textTouchSenzor = (TextView) findViewById(R.id.touchSenzor);
        final TextView textSoundSenzor = (TextView) findViewById(R.id.soundSenzor);
        final TextView textBatterySenzor = (TextView) findViewById(R.id.batterySenzor);
        final TextView textUltraSenzor = (TextView) findViewById(R.id.ultraSenzor);

        switchM.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                //ak je swtich zapnuty, zobrazime vsetky texty
                if (compoundButton.isChecked()){
                    //textBattery.setVisibility(View.VISIBLE);
                    textSound.setVisibility(View.VISIBLE);
                    textTouch.setVisibility(View.VISIBLE);
                    textLight.setVisibility(View.VISIBLE);
                    textUltra.setVisibility(View.VISIBLE);
                    textLightSenzor.setVisibility(View.VISIBLE);
                    textTouchSenzor.setVisibility(View.VISIBLE);
                    textSoundSenzor.setVisibility(View.VISIBLE);
                    //textBatterySenzor.setVisibility(View.VISIBLE);
                    textUltraSenzor.setVisibility(View.VISIBLE);
                    // ak je vypnuty tak to schovame
                }else{
                    //textBattery.setVisibility(View.INVISIBLE);
                    textSound.setVisibility(View.INVISIBLE);
                    textTouch.setVisibility(View.INVISIBLE);
                    textLight.setVisibility(View.INVISIBLE);
                    textUltra.setVisibility(View.INVISIBLE);
                    textLightSenzor.setVisibility(View.INVISIBLE);
                    textTouchSenzor.setVisibility(View.INVISIBLE);
                    textSoundSenzor.setVisibility(View.INVISIBLE);
                    //textBatterySenzor.setVisibility(View.INVISIBLE);
                    textUltraSenzor.setVisibility(View.INVISIBLE);
                    // a zaslanie prikazu aby nam nesvietil senzor na cerveno
                    sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_LIGHT, BTKomunikacia.LIGHT_INACTIVE, 0);
                }
            }
        });
    }

    // Vytvorenie noveho objektu pre komunikaciu s NXT robotom a odchytavanie handlerov
    public void createBTCommunicator() {
        myBTKomunikacia = new BTKomunikacia(this, myHandler, BluetoothAdapter.getDefaultAdapter(), getResources());
        btcHandler = myBTKomunikacia.getHandler();
    }

    // Vytvori a spusti vlakno pre komunikaciu cez BT s Nxt robotom
    // posielame mac adresu ako parameter
    public void startBTCommunicator(String mac_address) {
        connected = false;
        connectingProgressDialog = ProgressDialog.show(this, "", getResources().getString(R.string.connecting_please_wait), true);

        if (myBTKomunikacia != null) {
            try {
                myBTKomunikacia.destroyNXTconnection();
            }
            catch (IOException ignored) { }
        }
        createBTCommunicator();
        myBTKomunikacia.setMACAddress(mac_address);
        myBTKomunikacia.start();
    }

    // poslanie spravy na ukoncenie vlakna
    public void destroyBTCommunicator() {
        if (myBTKomunikacia != null) {
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.CLOSE, 0, 0);
            myBTKomunikacia = null;
        }
        connected = false;
    }

    // vratenie aktualneho stavu pripojenia k robotovi
    public boolean isConnected() {
       return connected;
    }

    // spustenie programu priamo v kocke
    public void startProgram(String name) {
        // spustenie programu, ktory sme nasli v kocke
        // .rxe su kompilovane programy, kt sme presunuli do kocky cez NXT-G
        // .rso su zvukove subory a .rpg su subory vytvorene v kocke pomocou "NXT PROGRAM" menu
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.START_PROGRAM, name);
    }

    // Nastavenie listeneru pre tlacidla, ktore su v layout.ovladanie
    public class DirectionButtonOnTouchListener implements View.OnTouchListener {
        public double leftMotor;
        public double rightMotor;

        public DirectionButtonOnTouchListener(double l, double r) {
            leftMotor = l;
            rightMotor = r;
        }

        // po zatlaceni tlacidla zoberie jeho hodnoty (left, right) a posle ich do kocky
        // na vykonanie pohybu, kazdy motor berie hodnoty od 1 do 100 pre pohyb vpred
        // zaporne hodnoty prinutia motor sa hybat naspat
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            // event na zatlacenie tlacidla
            if (action == MotionEvent.ACTION_DOWN) {
                // nasobenie hodnot, kt. berieme z metody onCreate() a nasobime ich rychlostou,
                // cize hodnoty su stale od -100 do 100
                updateMotorControl(leftMotor * power, rightMotor * power);
            } else if ((action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL)) {
                // ak prestaneme drzat tlacidlo, tak posleme hodnoty (0, 0), cize lavy a pravy motor
                // na zastavenie robota
                updateMotorControl(0, 0);
            }
            return true;
        }
    }

    // Posielanie parametrov na pohyb do tejto metody, ktora ju posle dalej na komunikaciu do nxt kocky
    public void updateMotorControl(double left, double right) {
        if (myBTKomunikacia != null) {
            // ak je hodnota 0, tak sa robot nehybe
            // ak posielame hodnoty 0 tak nebudeme predsa stale posielat, spravy ...
            if ((left == 0) && (right == 0)) {
                if (stopAlreadySent)
                    return;
                else
                    stopAlreadySent = true;
            }
            else
                stopAlreadySent = false;

            // posielanie sprav cez handler
            sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, (int) (left * directionLeft), 0);
            sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, (int) (right * directionRight), 0);
        }
    }

    // Presna metoda ako uvedena horsie, len posielame dalsi parameter
    // lavy motor, pravy motor a dlzku trvania
    public void updateMotorControlTime(double left, double right, int duration) {
        if (myBTKomunikacia != null) {
            if ((left == 0) && (right == 0)) {
                if (stopAlreadySent)
                    return;
                else
                    stopAlreadySent = true;
            }
            else
                stopAlreadySent = false;

            sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, (int) (left * directionLeft), duration);
            sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, (int) (right * directionRight), duration);
        }
    }

    // Posielanie sprav do BTKomunikatora a ten dalej spracuvava spravy do robota
    // parametre, ktore posielame su:
    // oneskorenie, sprava na vykonanie, prvy a druhy specialny parameter
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

    // Posielanie sprav do BTKomunikatora a ten dalej spracuvava spravy do robota
    // parametre, ktore posielame su:
    // oneskorenie, sprava na vykonanie, a "balicek" sprav
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

    // metoda, ktora sa spusti pri
    @Override
    public void onResume() {
        super.onResume();
        try {
        }
        catch (IndexOutOfBoundsException ex) {
            // ak sa nenasiel senzor
            showToast(R.string.sensor_initialization_failure, Toast.LENGTH_LONG);
            // prerusenie komunikatora
            destroyBTCommunicator();
            finish();
        }
    }

    // metoda, ktora sa spusti pri starte aktivity
    @Override
    protected void onStart() {
        super.onStart();

        // ak zariadenie nema BT, vypiseme hlasku
        if (BluetoothAdapter.getDefaultAdapter()==null) {
            showToast(R.string.bt_initialization_failure, Toast.LENGTH_LONG);
            destroyBTCommunicator();
            finish();
            return;
        }

        // nemame zapnute BT, tak spustime "aktivitu" na zapnutie BT
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // inac spustime vyber NXT robota
        } else {
            selectNXT();
        }
    }

    // metoda pri skonceni aktivity
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // znicime komunikator
        destroyBTCommunicator();
    }

    // pri pozastaveni aktivity ...
    @Override
    public void onPause() {
        destroyBTCommunicator();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
    }

    // zobrazenie spravy ako toast
    // posielame parametre textu, ktory zobrazime a dlzku zobrazenia
    public void showToast(String textToShow, int length) {
        reusableToast.setText(textToShow);
        reusableToast.setDuration(length);
        reusableToast.show();
    }

    // ta ista metoda ako hore ale namiesto textu posielame resource ID
    public void showToast(int resID, int length) {
        reusableToast.setText(resID);
        reusableToast.setDuration(length);
        reusableToast.show();
    }

    // spravy prijate z BTKomunikacie
    final Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message myMessage) {
            switch (myMessage.getData().getInt("message")) {
                // jednoduche zobrazenie spravy
                case BTKomunikacia.SHOW_MESSAGE:
                    showToast(myMessage.getData().getString("toastText"), Toast.LENGTH_SHORT);
                    break;
                // ak prijmeme spravu o pripojenom zariadeni; nastavime hodnotu connected;
                // vytvorime array listy pre programy a zvuky
                case BTKomunikacia.STATE_CONNECTED:
                    connected = true;
                    programList = new ArrayList<>();
                    soundList = new ArrayList<>();
                    // dame prec dialog o pripojeni
                    connectingProgressDialog.dismiss();
                    // sprava o ziskani suborov
                    sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.FIND_FILES, 0, 0);

                    // Timer, po ktorom sa spusti monitorovanie
                    // Preco ? Na zabranenie padov sposobenych posielanim spravy na ziskanie suborov
                    // ak sa tam priplietla ina sprava tak to nevedel spracovat string a padalo to
                    new CountDownTimer(5000, 1) {
                        @Override
                        public void onTick(long l) {
                        }

                        @Override
                        public void onFinish() {
                            // poslanie sprav o nastaveni senzorov
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_ULTRA, 0, 0);
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_SOUND, BTKomunikacia.DB, 0);
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_LIGHT, BTKomunikacia.REFLECTION, 0);
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_TOUCH, 0, 0);
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.LS_WRITE, 0, 0);
                            // a spusteni senzorov
                            dHandler.postDelayed(dRunnable, 100);
                            sHandler.postDelayed(sRunnable, 130);
                            zHandler.postDelayed(zRunnable, 160);
                            //bHandler.postDelayed(bRunnable, 190);
                            uHandler.postDelayed(uRunnable, 220);
                        }
                    }.start();
                    break;
                // po prijati spravy o stave motora ak sme pripojeny k nxt tak vratime spravu
                // o pozicii motora
                case BTKomunikacia.STATE_MOTOR:
                    if (myBTKomunikacia != null) {
                        byte[] motorMessage = myBTKomunikacia.getReturnMessage();
                        int position = byteToInt(motorMessage[21]) + (byteToInt(motorMessage[22]) << 8)
                                + (byteToInt(motorMessage[23]) << 16) + (byteToInt(motorMessage[24]) << 24);
                    }
                    break;
                // ak prijimeme chybovu spravu o parovani, dame prec dialog a zrusime komunikator
                case BTKomunikacia.STATE_ERROR_PAIRING:
                    connectingProgressDialog.dismiss();
                    destroyBTCommunicator();
                    break;
                // pri chybovej hlaske o pripojeni zrusime dialog
                case BTKomunikacia.STATE_ERROR_CONNECTING:
                    connectingProgressDialog.dismiss();
                    break;
                case BTKomunikacia.STATE_ERROR_RECIEVER:
                    break;
                // pri chybe pri posielani sprav
                case BTKomunikacia.STATE_ERROR_SENDING:
                    // zrusime komunikator
                    destroyBTCommunicator();
                    if (!btErrorPending) {
                        btErrorPending = true;
                        // ukazeme chybovu hlasku pomocou Alert Dialogu
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
                // sprava na zistenie firmware verzie
                case BTKomunikacia.FIRMWARE_VERSION:
                    if (myBTKomunikacia != null) {
                        byte[] firmwareMessage = myBTKomunikacia.getReturnMessage();
                        String minorVersionProtocol = String.valueOf(firmwareMessage[3]);
                        String majorVersionProtocol = String.valueOf(firmwareMessage[4]);
                        String minorVersionFirmware = String.valueOf(firmwareMessage[5]);
                        String majorVersionFirmware = String.valueOf(firmwareMessage[6]);

                        String firmwareVersion = "Protocol: " + majorVersionProtocol + "." + minorVersionProtocol +
                                "\n" + "Firmware: " + majorVersionFirmware + "." + minorVersionFirmware;
                    }
                    break;
                // zistenie informacii o robotovi (meno, BT adresa, BT signal, volna pamat)
                case BTKomunikacia.GET_DEVICE_INFO:
                    byte[] deviceMessage = myBTKomunikacia.getReturnMessage();
                    break;
                // najdene suborov
                case BTKomunikacia.FIND_FILES:
                    // ak sme pripojeny
                    if (myBTKomunikacia != null) {
                        byte[] fileMessage = myBTKomunikacia.getReturnMessage();

                        String fileName = new String(fileMessage, 4, 20);
                        fileName = fileName.replaceAll("\0", "");

                        // ak najdeme subory .rxe a .rpg pridame ich do zoznamu programov
                        if (fileName.endsWith(".rxe") || fileName.endsWith(".rpg")) {
                            programList.add(fileName);
                            // ak najdeme .rso pridame ich do zoznamu zvukov
                        } else if (fileName.endsWith(".rso")) {
                            soundList.add(fileName);
                        }
                        // najdeme dalsi subor
                        // limitujeme pocet programov aby sme sa nedostali do nekonecneho loopu
                        if (programList.size() <= MAX_PROGRAMS)
                            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.FIND_FILES,
                                    1, byteToInt(fileMessage[3]));
                    }
                    break;
                // sprava na zistenie stavu baterie
                case BTKomunikacia.BATTERY_INFO:
                    if (myBTKomunikacia != null) {
                        byte[] batteryMessage = myBTKomunikacia.getReturnMessage();
                        if (batteryMessage[1] == 0x0B) {
                            byte cbyte[] = new byte[2];
                            cbyte[0] = batteryMessage[3];
                            cbyte[1] = batteryMessage[4];

                            currentMiliVolts = Math.round(fromBytes(cbyte) / 100);
                        }
                        break;
                    }
                    // zistenie stavu o dotykovom senzore
                case BTKomunikacia.TOUCH_DATA:
                    if (myBTKomunikacia != null) {
                        byte[] touchMessage = myBTKomunikacia.getReturnMessage();
                        int nestlaceny = -1;
                        int stlaceny1 = -73;
                        int stlaceny2 = -75;

                        // ak prihnene spravu s portom senzoru 0
                        if (touchMessage[3] == 0) {
                            String touchData = String.valueOf(touchMessage[8]);

                            // ak nie je stlaceny senzor, hodnota = false a nastavime text do pola
                            if (touchData.equals(String.valueOf(nestlaceny))) {
                                dotykovySenzor = getResources().getString(R.string.touch0);
                                dotyk = false;
                                // to iste co vyssie ale naopak
                            } else if (touchData.equals(String.valueOf(stlaceny1))
                                    || touchData.equals(String.valueOf(stlaceny2))) {
                                dotykovySenzor = getResources().getString(R.string.touch1);
                                dotyk = true;
                            }
                        }
                    }
                    break;
                // zvukovy senzor
                case BTKomunikacia.SOUND_DATA:
                    // ak sme pripojeny
                    if (myBTKomunikacia != null) {
                        // zoberieme spravu zo streamu a priradime
                        byte[] soundMessage = myBTKomunikacia.getReturnMessage();

                        // ak prijmeme spravu o zvukovom senzore
                        if (soundMessage[3] == 1) {
                            byte cbyte[] = new byte[2];
                            cbyte[0] = soundMessage[10];
                            cbyte[1] = soundMessage[11];

                            // zoberieme raw data z horneho pola, cez metodu vytiahneme short
                            // vynasobime 100 a vydelime 1023 (max hodnota pri raw data)
                            currentSoundL = (fromBytes(cbyte) * 100) / 1023;
                        }
                    }
                    break;
                // svetelny senzor
                case BTKomunikacia.LIGHT_DATA:
                    if (myBTKomunikacia != null) {
                        byte[] lightMessage = myBTKomunikacia.getReturnMessage();

                        if (lightMessage[3] == 2) {
                            byte cbyte[] = new byte[2];
                            cbyte[0] = lightMessage[10];
                            cbyte[1] = lightMessage[11];

                            currentLightL = (fromBytes(cbyte) * 100) / 1023;
                        }
                    }
                    break;
                // vypusta zle data spat
                case BTKomunikacia.ULTRASONIC_DATA:
                    if (myBTKomunikacia != null) {
                        byte[] ultrasonicMessage = myBTKomunikacia.getReturnMessage();

                        if (ultrasonicMessage[3] == 3) {
                            currentUltrasonicL = (ultrasonicMessage[10]) * (-1);
                        }
                    }
                    break;
                // UNUSED
                case BTKomunikacia.LS_DATA:
                    if (myBTKomunikacia != null) {
                        byte[] lsDataMessage = myBTKomunikacia.getReturnMessage();
                    }
                    break;
            }
        }
    };

    // prevedenie bytov na int
    public int byteToInt(byte byteValue) {
        int intValue = (byteValue & (byte) 0x7f);

        if ((byteValue & (byte) 0x80) != 0)
            intValue |= 0x80;
        return intValue;
    }

    // prevedenie bajtov z pola
    public static int fromBytes(byte cbyte[])
    {
        ByteBuffer bytebuffer = ByteBuffer.wrap(cbyte);
        bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
        if (cbyte.length == 2)
        {
            return bytebuffer.getShort();
        }
        if (cbyte.length == 4)
        {
            return bytebuffer.getInt();
        } else
        {
            return 0;
        }
    }

    // zaslanie spravy o ziskani dat z dotykoveho senzora
    public void udajeTouch(){
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_TOUCH_INFO, 0, 0);
    }

    // zaslanie spravy o ziskani dat zo zvukoveho senzora
    public void udajeSound(){
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_SOUND_INFO, 0, 0);
    }

    // zaslanie spravy o ziskani dat zo svetelneho senzora
    public void udajeLight(){
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_LIGHT_INFO, 0, 0);
    }

    // zaslanie spravy o ziskani dat z ultrazvukoveho senzora
    public void udajeUltrasonic(){
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_ULTRASONIC_INFO, 0, 0);
        //sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.LS_READ, 0, 0);
    }

    // najdenie NXT zariadenia
    void selectNXT() {
        Intent serverIntent = new Intent(this, ZoznamZariadeni.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    // vyledok aktivity
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // ak pozadujeme pripojenie k zariadeniu
            case REQUEST_CONNECT_DEVICE:
                // ked zoznam zariadeni vrati zariadenie, ku ktoremu sa mozme pripojit
                if (resultCode == Activity.RESULT_OK) {
                    // ziskame MAC adresu a spustime nove vlakno pre pripojenie
                    String address = data.getExtras().getString(ZoznamZariadeni.EXTRA_ADRESA_ZARIADENIA);
                    pairing = data.getExtras().getBoolean(ZoznamZariadeni.PAROVANIE);
                    startBTCommunicator(address);
                }
                break;
            // ak pozadujeme zapnutie BT
            case REQUEST_ENABLE_BT:
                switch (resultCode) {
                    case Activity.RESULT_OK:
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

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon1(View view){
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        registerForContextMenu(field1);
        this.openContextMenu(field1);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon2(View view){
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        registerForContextMenu(field2);
        this.openContextMenu(field2);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon3(View view){
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        registerForContextMenu(field3);
        this.openContextMenu(field3);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon4(View view){
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        registerForContextMenu(field4);
        this.openContextMenu(field4);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon5(View view){
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        registerForContextMenu(field5);
        this.openContextMenu(field5);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon6(View view){
        ImageButton field6 = (ImageButton) findViewById(R.id.Field6);
        registerForContextMenu(field6);
        this.openContextMenu(field6);
    }

    // zmenenie ikony pola v programovani aktivity a registrovanie do context menu
    public void changeIcon7(View view){
        ImageButton field7 = (ImageButton) findViewById(R.id.Field7);
        registerForContextMenu(field7);
        this.openContextMenu(field7);
    }

    // po vytvoreni context menu
    @Override
    public void onCreateContextMenu(ContextMenu cMenu, View view, ContextMenu.ContextMenuInfo cMenuInfo) {
        // naplnime ho
        super.onCreateContextMenu(cMenu, view, cMenuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_programovanie, cMenu);
        // zistime ID pola
        buttonID = view.getId();
        // a potom nastavime bool na pole, kt mame
        if (buttonID==R.id.Field1){
            command1 = true;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = false;
            command6 = false;
            command7 = false;
        } else if (buttonID==R.id.Field2){
            command1 = false;
            command2 = true;
            command3 = false;
            command4 = false;
            command5 = false;
            command6 = false;
            command7 = false;
        } else if (buttonID==R.id.Field3){
            command1 = false;
            command2 = false;
            command3 = true;
            command4 = false;
            command5 = false;
            command6 = false;
            command7 = false;
        } else if (buttonID==R.id.Field4){
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = true;
            command5 = false;
            command6 = false;
            command7 = false;
        } else if (buttonID==R.id.Field5){
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = true;
            command6 = false;
            command7 = false;
        } else if (buttonID==R.id.Field6){
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = false;
            command6 = true;
            command7 = false;
        } else if (buttonID==R.id.Field7){
            command1 = false;
            command2 = false;
            command3 = false;
            command4 = false;
            command5 = false;
            command6 = false;
            command7 = true;
        }
    }

    // ked klikneme na kontext menu ...
    @Override
    public boolean onContextItemSelected(MenuItem cItem) {
        // deklarovanie premen z poli
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        ImageButton field6 = (ImageButton) findViewById(R.id.Field6);
        ImageButton field7 = (ImageButton) findViewById(R.id.Field7);

        // zistenie na kazdom buttone, na co sme klikli a podla toho nadstavime "ikonu"
        // a nadstavime flag v podobe cisla, kt. neskor pouzijeme na zistenie metody na vykonanie
        if(command1){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field1.setImageResource(R.drawable.forward1);
                    field1.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field1.setImageResource(R.drawable.forward3);
                    field1.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field1.setImageResource(R.drawable.forward5);
                    field1.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field1.setImageResource(R.drawable.forward10);
                    field1.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field1.setImageResource(R.drawable.backward1);
                    field1.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field1.setImageResource(R.drawable.backward3);
                    field1.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field1.setImageResource(R.drawable.backward5);
                    field1.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field1.setImageResource(R.drawable.backward10);
                    field1.setContentDescription("8");
                    break;
                case R.id.left:
                    field1.setImageResource(R.drawable.arrow_left);
                    field1.setContentDescription("9");
                    break;
                case R.id.right:
                    field1.setImageResource(R.drawable.arrow_right);
                    field1.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field1.setImageResource(R.drawable.wait1);
                    field1.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field1.setImageResource(R.drawable.wait3);
                    field1.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field1.setImageResource(R.drawable.wait5);
                    field1.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field1.setImageResource(R.drawable.wait10);
                    field1.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field1.setImageResource(R.drawable.touch_pressed);
                    field1.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field1.setImageResource(R.drawable.sound_lt_25);
                    field1.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field1.setImageResource(R.drawable.sound_lt_50);
                    field1.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field1.setImageResource(R.drawable.sound_lt_75);
                    field1.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field1.setImageResource(R.drawable.sound_gt_25);
                    field1.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field1.setImageResource(R.drawable.sound_gt_50);
                    field1.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field1.setImageResource(R.drawable.sound_gt_75);
                    field1.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field1.setImageResource(R.drawable.light_lt_25);
                    field1.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field1.setImageResource(R.drawable.light_lt_50);
                    field1.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field1.setImageResource(R.drawable.light_lt_75);
                    field1.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field1.setImageResource(R.drawable.light_gt_25);
                    field1.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field1.setImageResource(R.drawable.light_gt_50);
                    field1.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field1.setImageResource(R.drawable.light_gt_75);
                    field1.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field1.setImageResource(R.drawable.ultra_lt_25);
                    field1.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field1.setImageResource(R.drawable.ultra_gt_25);
                    field1.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        }
        else if(command2){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field2.setImageResource(R.drawable.forward1);
                    field2.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field2.setImageResource(R.drawable.forward3);
                    field2.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field2.setImageResource(R.drawable.forward5);
                    field2.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field2.setImageResource(R.drawable.forward10);
                    field2.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field2.setImageResource(R.drawable.backward1);
                    field2.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field2.setImageResource(R.drawable.backward3);
                    field2.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field2.setImageResource(R.drawable.backward5);
                    field2.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field2.setImageResource(R.drawable.backward10);
                    field2.setContentDescription("8");
                    break;
                case R.id.left:
                    field2.setImageResource(R.drawable.arrow_left);
                    field2.setContentDescription("9");
                    break;
                case R.id.right:
                    field2.setImageResource(R.drawable.arrow_right);
                    field2.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field2.setImageResource(R.drawable.wait1);
                    field2.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field2.setImageResource(R.drawable.wait3);
                    field2.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field2.setImageResource(R.drawable.wait5);
                    field2.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field2.setImageResource(R.drawable.wait10);
                    field2.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field2.setImageResource(R.drawable.touch_pressed);
                    field2.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field2.setImageResource(R.drawable.sound_lt_25);
                    field2.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field2.setImageResource(R.drawable.sound_lt_50);
                    field2.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field2.setImageResource(R.drawable.sound_lt_75);
                    field2.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field2.setImageResource(R.drawable.sound_gt_25);
                    field2.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field2.setImageResource(R.drawable.sound_gt_50);
                    field2.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field2.setImageResource(R.drawable.sound_gt_75);
                    field2.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field2.setImageResource(R.drawable.light_lt_25);
                    field2.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field2.setImageResource(R.drawable.light_lt_50);
                    field2.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field2.setImageResource(R.drawable.light_lt_75);
                    field2.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field2.setImageResource(R.drawable.light_gt_25);
                    field2.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field2.setImageResource(R.drawable.light_gt_50);
                    field2.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field2.setImageResource(R.drawable.light_gt_75);
                    field2.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field2.setImageResource(R.drawable.ultra_lt_25);
                    field2.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field2.setImageResource(R.drawable.ultra_gt_25);
                    field2.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        }
        else if(command3){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field3.setImageResource(R.drawable.forward1);
                    field3.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field3.setImageResource(R.drawable.forward3);
                    field3.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field3.setImageResource(R.drawable.forward5);
                    field3.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field3.setImageResource(R.drawable.forward10);
                    field3.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field3.setImageResource(R.drawable.backward1);
                    field3.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field3.setImageResource(R.drawable.backward3);
                    field3.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field3.setImageResource(R.drawable.backward5);
                    field3.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field3.setImageResource(R.drawable.backward10);
                    field3.setContentDescription("8");
                    break;
                case R.id.left:
                    field3.setImageResource(R.drawable.arrow_left);
                    field3.setContentDescription("9");
                    break;
                case R.id.right:
                    field3.setImageResource(R.drawable.arrow_right);
                    field3.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field3.setImageResource(R.drawable.wait1);
                    field3.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field3.setImageResource(R.drawable.wait3);
                    field3.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field3.setImageResource(R.drawable.wait5);
                    field3.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field3.setImageResource(R.drawable.wait10);
                    field3.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field3.setImageResource(R.drawable.touch_pressed);
                    field3.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field3.setImageResource(R.drawable.sound_lt_25);
                    field3.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field3.setImageResource(R.drawable.sound_lt_50);
                    field3.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field3.setImageResource(R.drawable.sound_lt_75);
                    field3.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field3.setImageResource(R.drawable.sound_gt_25);
                    field3.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field3.setImageResource(R.drawable.sound_gt_50);
                    field3.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field3.setImageResource(R.drawable.sound_gt_75);
                    field3.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field3.setImageResource(R.drawable.light_lt_25);
                    field3.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field3.setImageResource(R.drawable.light_lt_50);
                    field3.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field3.setImageResource(R.drawable.light_lt_75);
                    field3.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field3.setImageResource(R.drawable.light_gt_25);
                    field3.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field3.setImageResource(R.drawable.light_gt_50);
                    field3.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field3.setImageResource(R.drawable.light_gt_75);
                    field3.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field3.setImageResource(R.drawable.ultra_lt_25);
                    field3.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field3.setImageResource(R.drawable.ultra_gt_25);
                    field3.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        }
        else if(command4){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field4.setImageResource(R.drawable.forward1);
                    field4.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field4.setImageResource(R.drawable.forward3);
                    field4.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field4.setImageResource(R.drawable.forward5);
                    field4.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field4.setImageResource(R.drawable.forward10);
                    field4.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field4.setImageResource(R.drawable.backward1);
                    field4.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field4.setImageResource(R.drawable.backward3);
                    field4.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field4.setImageResource(R.drawable.backward5);
                    field4.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field4.setImageResource(R.drawable.backward10);
                    field4.setContentDescription("8");
                    break;
                case R.id.left:
                    field4.setImageResource(R.drawable.arrow_left);
                    field4.setContentDescription("9");
                    break;
                case R.id.right:
                    field4.setImageResource(R.drawable.arrow_right);
                    field4.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field4.setImageResource(R.drawable.wait1);
                    field4.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field4.setImageResource(R.drawable.wait3);
                    field4.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field4.setImageResource(R.drawable.wait5);
                    field4.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field4.setImageResource(R.drawable.wait10);
                    field4.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field4.setImageResource(R.drawable.touch_pressed);
                    field4.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field4.setImageResource(R.drawable.sound_lt_25);
                    field4.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field4.setImageResource(R.drawable.sound_lt_50);
                    field4.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field4.setImageResource(R.drawable.sound_lt_75);
                    field4.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field4.setImageResource(R.drawable.sound_gt_25);
                    field4.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field4.setImageResource(R.drawable.sound_gt_50);
                    field4.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field4.setImageResource(R.drawable.sound_gt_75);
                    field4.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field4.setImageResource(R.drawable.light_lt_25);
                    field4.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field4.setImageResource(R.drawable.light_lt_50);
                    field4.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field4.setImageResource(R.drawable.light_lt_75);
                    field4.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field4.setImageResource(R.drawable.light_gt_25);
                    field4.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field4.setImageResource(R.drawable.light_gt_50);
                    field4.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field4.setImageResource(R.drawable.light_gt_75);
                    field4.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field4.setImageResource(R.drawable.ultra_lt_25);
                    field4.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field4.setImageResource(R.drawable.ultra_gt_25);
                    field4.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        }
        else if(command5){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field5.setImageResource(R.drawable.forward1);
                    field5.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field5.setImageResource(R.drawable.forward3);
                    field5.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field5.setImageResource(R.drawable.forward5);
                    field5.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field5.setImageResource(R.drawable.forward10);
                    field5.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field5.setImageResource(R.drawable.backward1);
                    field5.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field5.setImageResource(R.drawable.backward3);
                    field5.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field5.setImageResource(R.drawable.backward5);
                    field5.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field5.setImageResource(R.drawable.backward10);
                    field5.setContentDescription("8");
                    break;
                case R.id.left:
                    field5.setImageResource(R.drawable.arrow_left);
                    field5.setContentDescription("9");
                    break;
                case R.id.right:
                    field5.setImageResource(R.drawable.arrow_right);
                    field5.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field5.setImageResource(R.drawable.wait1);
                    field5.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field5.setImageResource(R.drawable.wait3);
                    field5.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field5.setImageResource(R.drawable.wait5);
                    field5.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field5.setImageResource(R.drawable.wait10);
                    field5.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field5.setImageResource(R.drawable.touch_pressed);
                    field5.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field5.setImageResource(R.drawable.sound_lt_25);
                    field5.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field5.setImageResource(R.drawable.sound_lt_50);
                    field5.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field5.setImageResource(R.drawable.sound_lt_75);
                    field5.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field5.setImageResource(R.drawable.sound_gt_25);
                    field5.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field5.setImageResource(R.drawable.sound_gt_50);
                    field5.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field5.setImageResource(R.drawable.sound_gt_75);
                    field5.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field5.setImageResource(R.drawable.light_lt_25);
                    field5.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field5.setImageResource(R.drawable.light_lt_50);
                    field5.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field5.setImageResource(R.drawable.light_lt_75);
                    field5.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field5.setImageResource(R.drawable.light_gt_25);
                    field5.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field5.setImageResource(R.drawable.light_gt_50);
                    field5.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field5.setImageResource(R.drawable.light_gt_75);
                    field5.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field5.setImageResource(R.drawable.ultra_lt_25);
                    field5.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field5.setImageResource(R.drawable.ultra_gt_25);
                    field5.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        } else if(command6){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field6.setImageResource(R.drawable.forward1);
                    field6.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field6.setImageResource(R.drawable.forward3);
                    field6.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field6.setImageResource(R.drawable.forward5);
                    field6.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field6.setImageResource(R.drawable.forward10);
                    field6.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field6.setImageResource(R.drawable.backward1);
                    field6.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field6.setImageResource(R.drawable.backward3);
                    field6.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field6.setImageResource(R.drawable.backward5);
                    field6.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field6.setImageResource(R.drawable.backward10);
                    field6.setContentDescription("8");
                    break;
                case R.id.left:
                    field6.setImageResource(R.drawable.arrow_left);
                    field6.setContentDescription("9");
                    break;
                case R.id.right:
                    field6.setImageResource(R.drawable.arrow_right);
                    field6.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field6.setImageResource(R.drawable.wait1);
                    field6.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field6.setImageResource(R.drawable.wait3);
                    field6.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field6.setImageResource(R.drawable.wait5);
                    field6.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field6.setImageResource(R.drawable.wait10);
                    field6.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field6.setImageResource(R.drawable.touch_pressed);
                    field6.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field6.setImageResource(R.drawable.sound_lt_25);
                    field6.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field6.setImageResource(R.drawable.sound_lt_50);
                    field6.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field6.setImageResource(R.drawable.sound_lt_75);
                    field6.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field6.setImageResource(R.drawable.sound_gt_25);
                    field6.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field6.setImageResource(R.drawable.sound_gt_50);
                    field6.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field6.setImageResource(R.drawable.sound_gt_75);
                    field6.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field6.setImageResource(R.drawable.light_lt_25);
                    field6.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field6.setImageResource(R.drawable.light_lt_50);
                    field6.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field6.setImageResource(R.drawable.light_lt_75);
                    field6.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field6.setImageResource(R.drawable.light_gt_25);
                    field6.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field6.setImageResource(R.drawable.light_gt_50);
                    field6.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field6.setImageResource(R.drawable.light_gt_75);
                    field6.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field6.setImageResource(R.drawable.ultra_lt_25);
                    field6.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field6.setImageResource(R.drawable.ultra_gt_25);
                    field6.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        } else if(command7){
            switch (cItem.getItemId()) {
                case R.id.forward1:
                    field7.setImageResource(R.drawable.forward1);
                    field7.setContentDescription("1");
                    break;
                case R.id.forward3:
                    field7.setImageResource(R.drawable.forward3);
                    field7.setContentDescription("2");
                    break;
                case R.id.forward5:
                    field7.setImageResource(R.drawable.forward5);
                    field7.setContentDescription("3");
                    break;
                case R.id.forward10:
                    field7.setImageResource(R.drawable.forward10);
                    field7.setContentDescription("4");
                    break;
                case R.id.backward1:
                    field7.setImageResource(R.drawable.backward1);
                    field7.setContentDescription("5");
                    break;
                case R.id.backward3:
                    field7.setImageResource(R.drawable.backward3);
                    field7.setContentDescription("6");
                    break;
                case R.id.backward5:
                    field7.setImageResource(R.drawable.backward5);
                    field7.setContentDescription("7");
                    break;
                case R.id.backward10:
                    field7.setImageResource(R.drawable.backward10);
                    field7.setContentDescription("8");
                    break;
                case R.id.left:
                    field7.setImageResource(R.drawable.arrow_left);
                    field7.setContentDescription("9");
                    break;
                case R.id.right:
                    field7.setImageResource(R.drawable.arrow_right);
                    field7.setContentDescription("10");
                    break;
                case R.id.wait1:
                    field7.setImageResource(R.drawable.wait1);
                    field7.setContentDescription("11");
                    break;
                case R.id.wait3:
                    field7.setImageResource(R.drawable.wait3);
                    field7.setContentDescription("12");
                    break;
                case R.id.wait5:
                    field7.setImageResource(R.drawable.wait5);
                    field7.setContentDescription("13");
                    break;
                case R.id.wait10:
                    field7.setImageResource(R.drawable.wait10);
                    field7.setContentDescription("14");
                    break;
                case R.id.dotykSenzor11:
                    field7.setImageResource(R.drawable.touch_pressed);
                    field7.setContentDescription("15");
                    break;
                case R.id.soundSensorM25:
                    field7.setImageResource(R.drawable.sound_lt_25);
                    field7.setContentDescription("16");
                    break;
                case R.id.soundSensorM50:
                    field7.setImageResource(R.drawable.sound_lt_50);
                    field7.setContentDescription("17");
                    break;
                case R.id.soundSensorM75:
                    field7.setImageResource(R.drawable.sound_lt_75);
                    field7.setContentDescription("18");
                    break;
                case R.id.soundSensorV25:
                    field7.setImageResource(R.drawable.sound_gt_25);
                    field7.setContentDescription("19");
                    break;
                case R.id.soundSensorV50:
                    field7.setImageResource(R.drawable.sound_gt_50);
                    field7.setContentDescription("20");
                    break;
                case R.id.soundSensorV75:
                    field7.setImageResource(R.drawable.sound_gt_75);
                    field7.setContentDescription("21");
                    break;
                case R.id.lightSensorM25:
                    field7.setImageResource(R.drawable.light_lt_25);
                    field7.setContentDescription("22");
                    break;
                case R.id.lightSensorM50:
                    field7.setImageResource(R.drawable.light_lt_50);
                    field7.setContentDescription("23");
                    break;
                case R.id.lightSensorM75:
                    field7.setImageResource(R.drawable.light_lt_75);
                    field7.setContentDescription("24");
                    break;
                case R.id.lightSensorV25:
                    field7.setImageResource(R.drawable.light_gt_25);
                    field7.setContentDescription("25");
                    break;
                case R.id.lightSensorV50:
                    field7.setImageResource(R.drawable.light_gt_50);
                    field7.setContentDescription("26");
                    break;
                case R.id.lightSensorV75:
                    field7.setImageResource(R.drawable.light_gt_75);
                    field7.setContentDescription("27");
                    break;
                case R.id.ultraZvukovyM25:
                    field7.setImageResource(R.drawable.ultra_lt_25);
                    field7.setContentDescription("28");
                    break;
                case R.id.ultraZvukovyV25:
                    field7.setImageResource(R.drawable.ultra_gt_25);
                    field7.setContentDescription("29");
                    break;
                default:
                    return super.onContextItemSelected(cItem);
            }
        }
        return true;
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod1(){
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        if(field1.getContentDescription() == "1"){
            goForward1();
            casovac1 = time1;
        } else if (field1.getContentDescription() == "2"){
            goForward3();
            casovac1 = time3;
        } else if (field1.getContentDescription() == "3"){
            goForward5();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "4"){
            goForward10();
            casovac1 = time10;
        } else if (field1.getContentDescription() == "5"){
            goBackward1();
            casovac1 = time1;
        } else if (field1.getContentDescription() == "6"){
            goBackward3();
            casovac1 = time3;
        } else if (field1.getContentDescription() == "7"){
            goBackward5();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "8"){
            goBackward10();
            casovac1 = time10;
        } else if (field1.getContentDescription() == "9"){
            turnLeft();
            casovac1 = time1;
        } else if (field1.getContentDescription() == "10"){
            turnRight();
            casovac1 = time1;
        } else if (field1.getContentDescription() == "11"){
            wait1();
            casovac1 = time1;
        } else if (field1.getContentDescription() == "12"){
            wait3();
            casovac1 = time3;
        } else if (field1.getContentDescription() == "13"){
            wait5();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "14"){
            wait10();
            casovac1 = time10;
        } else if (field1.getContentDescription() == "15"){
            dotykMetoda();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "16"){
            soundM25();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "17"){
            soundM50();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "18"){
            soundM75();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "19"){
            soundV25();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "20"){
            soundV50();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "21"){
            soundV75();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "22"){
            lightM25();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "23"){
            lightM50();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "24"){
            lightM75();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "25"){
            lightV25();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "26"){
            lightV50();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "27"){
            lightV75();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "28"){
            ultraM25();
            casovac1 = time5;
        } else if (field1.getContentDescription() == "29"){
            ultraV25();
            casovac1 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod2(){
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        if(field2.getContentDescription() == "1"){
            goForward1();
            casovac2 = time1;
        } else if (field2.getContentDescription() == "2"){
            goForward3();
            casovac2 = time3;
        } else if (field2.getContentDescription() == "3"){
            goForward5();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "4"){
            goForward10();
            casovac2 = time10;
        } else if (field2.getContentDescription() == "5"){
            goBackward1();
            casovac2 = time1;
        } else if (field2.getContentDescription() == "6"){
            goBackward3();
            casovac2 = time3;
        } else if (field2.getContentDescription() == "7"){
            goBackward5();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "8"){
            goBackward10();
            casovac2 = time10;
        } else if (field2.getContentDescription() == "9"){
            turnLeft();
            casovac2 = time1;
        } else if (field2.getContentDescription() == "10"){
            turnRight();
            casovac2 = time1;
        } else if (field2.getContentDescription() == "11"){
            wait1();
            casovac2 = time1;
        } else if (field2.getContentDescription() == "12"){
            wait3();
            casovac2 = time3;
        } else if (field2.getContentDescription() == "13"){
            wait5();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "14"){
            wait10();
            casovac2 = time10;
        } else if (field2.getContentDescription() == "15"){
            dotykMetoda();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "16"){
            soundM25();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "17"){
            soundM50();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "18"){
            soundM75();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "19"){
            soundV25();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "20"){
            soundV50();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "21"){
            soundV75();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "22"){
            lightM25();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "23"){
            lightM50();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "24"){
            lightM75();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "25"){
            lightV25();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "26"){
            lightV50();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "27"){
            lightV75();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "28"){
            ultraM25();
            casovac2 = time5;
        } else if (field2.getContentDescription() == "29"){
            ultraV25();
            casovac2 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod3(){
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        if(field3.getContentDescription() == "1"){
            goForward1();
            casovac3 = time1;
        } else if (field3.getContentDescription() == "2"){
            goForward3();
            casovac3 = time3;
        } else if (field3.getContentDescription() == "3"){
            goForward5();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "4"){
            goForward10();
            casovac3 = time10;
        } else if (field3.getContentDescription() == "5"){
            goBackward1();
            casovac3 = time1;
        } else if (field3.getContentDescription() == "6"){
            goBackward3();
            casovac3 = time3;
        } else if (field3.getContentDescription() == "7"){
            goBackward5();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "8"){
            goBackward10();
            casovac3 = time10;
        } else if (field3.getContentDescription() == "9"){
            turnLeft();
            casovac3 = time1;
        } else if (field3.getContentDescription() == "10"){
            turnRight();
            casovac3 = time1;
        } else if (field3.getContentDescription() == "11"){
            wait1();
            casovac3 = time1;
        } else if (field3.getContentDescription() == "12"){
            wait3();
            casovac3 = time3;
        } else if (field3.getContentDescription() == "13"){
            wait5();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "14"){
            wait10();
            casovac3 = time10;
        } else if (field3.getContentDescription() == "15"){
            dotykMetoda();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "16"){
            soundM25();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "17"){
            soundM50();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "18"){
            soundM75();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "19"){
            soundV25();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "20"){
            soundV50();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "21"){
            soundV75();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "22"){
            lightM25();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "23"){
            lightM50();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "24"){
            lightM75();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "25"){
            lightV25();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "26"){
            lightV50();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "27"){
            lightV75();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "28"){
            ultraM25();
            casovac3 = time5;
        } else if (field3.getContentDescription() == "29"){
            ultraV25();
            casovac3 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod4(){
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        if(field4.getContentDescription() == "1"){
            goForward1();
            casovac4 = time1;
        } else if (field4.getContentDescription() == "2"){
            goForward3();
            casovac4 = time3;
        } else if (field4.getContentDescription() == "3"){
            goForward5();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "4"){
            goForward10();
            casovac4 = time10;
        } else if (field4.getContentDescription() == "5"){
            goBackward1();
            casovac4 = time1;
        } else if (field4.getContentDescription() == "6"){
            goBackward3();
            casovac4 = time3;
        } else if (field4.getContentDescription() == "7"){
            goBackward5();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "8"){
            goBackward10();
            casovac4 = time10;
        } else if (field4.getContentDescription() == "9"){
            turnLeft();
            casovac4 = time1;
        } else if (field4.getContentDescription() == "10"){
            turnRight();
            casovac4 = time1;
        } else if (field4.getContentDescription() == "11"){
            wait1();
            casovac4 = time1;
        } else if (field4.getContentDescription() == "12"){
            wait3();
            casovac4 = time3;
        } else if (field4.getContentDescription() == "13"){
            wait5();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "14"){
            wait10();
            casovac4 = time10;
        } else if (field4.getContentDescription() == "15"){
            dotykMetoda();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "16"){
            soundM25();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "17"){
            soundM50();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "18"){
            soundM75();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "19"){
            soundV25();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "20"){
            soundV50();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "21"){
            soundV75();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "22"){
            lightM25();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "23"){
            lightM50();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "24"){
            lightM75();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "25"){
            lightV25();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "26"){
            lightV50();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "27"){
            lightV75();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "28"){
            ultraM25();
            casovac4 = time5;
        } else if (field4.getContentDescription() == "29"){
            ultraV25();
            casovac4 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod5(){
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        if(field5.getContentDescription() == "1"){
            goForward1();
            casovac5 = time1;
        } else if (field5.getContentDescription() == "2"){
            goForward3();
            casovac5 = time3;
        } else if (field5.getContentDescription() == "3"){
            goForward5();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "4"){
            goForward10();
            casovac5 = time10;
        } else if (field5.getContentDescription() == "5"){
            goBackward1();
            casovac5 = time1;
        } else if (field5.getContentDescription() == "6"){
            goBackward3();
            casovac5 = time3;
        } else if (field5.getContentDescription() == "7"){
            goBackward5();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "8"){
            goBackward10();
            casovac5 = time10;
        } else if (field5.getContentDescription() == "9"){
            turnLeft();
            casovac5 = time1;
        } else if (field5.getContentDescription() == "10"){
            turnRight();
            casovac5 = time1;
        } else if (field5.getContentDescription() == "11"){
            wait1();
            casovac5 = time1;
        } else if (field5.getContentDescription() == "12"){
            wait3();
            casovac5 = time3;
        } else if (field5.getContentDescription() == "13"){
            wait5();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "14"){
            wait10();
            casovac5 = time10;
        } else if (field5.getContentDescription() == "15"){
            dotykMetoda();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "16"){
            soundM25();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "17"){
            soundM50();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "18"){
            soundM75();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "19"){
            soundV25();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "20"){
            soundV50();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "21"){
            soundV75();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "22"){
            lightM25();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "23"){
            lightM50();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "24"){
            lightM75();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "25"){
            lightV25();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "26"){
            lightV50();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "27"){
            lightV75();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "28"){
            ultraM25();
            casovac5 = time5;
        } else if (field5.getContentDescription() == "29"){
            ultraV25();
            casovac5 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod6(){
        ImageButton field6 = (ImageButton) findViewById(R.id.Field6);
        if(field6.getContentDescription() == "1"){
            goForward1();
            casovac6 = time1;
        } else if (field6.getContentDescription() == "2"){
            goForward3();
            casovac6 = time3;
        } else if (field6.getContentDescription() == "3"){
            goForward5();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "4"){
            goForward10();
            casovac6 = time10;
        } else if (field6.getContentDescription() == "5"){
            goBackward1();
            casovac6 = time1;
        } else if (field6.getContentDescription() == "6"){
            goBackward3();
            casovac6 = time3;
        } else if (field6.getContentDescription() == "7"){
            goBackward5();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "8"){
            goBackward10();
            casovac6 = time10;
        } else if (field6.getContentDescription() == "9"){
            turnLeft();
            casovac6 = time1;
        } else if (field6.getContentDescription() == "10"){
            turnRight();
            casovac6 = time1;
        } else if (field6.getContentDescription() == "11"){
            wait1();
            casovac6 = time1;
        } else if (field6.getContentDescription() == "12"){
            wait3();
            casovac6 = time3;
        } else if (field6.getContentDescription() == "13"){
            wait5();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "14"){
            wait10();
            casovac6 = time10;
        } else if (field6.getContentDescription() == "15"){
            dotykMetoda();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "16"){
            soundM25();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "17"){
            soundM50();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "18"){
            soundM75();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "19"){
            soundV25();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "20"){
            soundV50();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "21"){
            soundV75();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "22"){
            lightM25();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "23"){
            lightM50();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "24"){
            lightM75();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "25"){
            lightV25();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "26"){
            lightV50();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "27"){
            lightV75();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "28"){
            ultraM25();
            casovac6 = time5;
        } else if (field6.getContentDescription() == "29"){
            ultraV25();
            casovac6 = time5;
        }
    }

    // metoda pre pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktoru vieme z metod
    // nastavime hodnotu castovacu podla casov - ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod7(){
        ImageButton field7 = (ImageButton) findViewById(R.id.Field7);
        if(field7.getContentDescription() == "1"){
            goForward1();
        } else if (field7.getContentDescription() == "2"){
            goForward3();
        } else if (field7.getContentDescription() == "3"){
            goForward5();
        } else if (field7.getContentDescription() == "4"){
            goForward10();
        } else if (field7.getContentDescription() == "5"){
            goBackward1();
        } else if (field7.getContentDescription() == "6"){
            goBackward3();
        } else if (field7.getContentDescription() == "7"){
            goBackward5();
        } else if (field7.getContentDescription() == "8"){
            goBackward10();
        } else if (field7.getContentDescription() == "9"){
            turnLeft();
        } else if (field7.getContentDescription() == "10"){
            turnRight();
        } else if (field7.getContentDescription() == "11"){
            wait1();
        } else if (field7.getContentDescription() == "12"){
            wait3();
        } else if (field7.getContentDescription() == "13"){
            wait5();
        } else if (field7.getContentDescription() == "14"){
            wait10();
        } else if (field7.getContentDescription() == "15"){
            dotykMetoda();
        } else if (field7.getContentDescription() == "16"){
            soundM25();
        } else if (field7.getContentDescription() == "17"){
            soundM50();
        } else if (field7.getContentDescription() == "18"){
            soundM75();
        } else if (field7.getContentDescription() == "19"){
            soundV25();
        } else if (field7.getContentDescription() == "20"){
            soundV50();
        } else if (field7.getContentDescription() == "21"){
            soundV75();
        } else if (field7.getContentDescription() == "22"){
            lightM25();
        } else if (field7.getContentDescription() == "23"){
            lightM50();
        } else if (field7.getContentDescription() == "24"){
            lightM75();
        } else if (field7.getContentDescription() == "25"){
            lightV25();
        } else if (field7.getContentDescription() == "26"){
            lightV50();
        } else if (field7.getContentDescription() == "27"){
            lightV75();
        } else if (field7.getContentDescription() == "28"){
            ultraM25();
        } else if (field7.getContentDescription() == "29"){
            ultraV25();
        }
    }

    // metoda na pohyb v pred, posielame s parametrom (cas = sekundy)
    public void goForward1(){
        splnenyPohyb = false;
        // do metody posielame v podstate rychlost (co je v tomto pripade 100) a cas
        updateMotorControlTime(100, 100, 1);
        // spustenie timeru s parametrom casu, kt. sme  zadali
        new CountDownTimer(time1-1000, 1) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // po skonceni countdowntimeru, posleme zase spravu o "zastaveni" motorov
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb v pred, posielame s parametrom (cas = sekundy)
    public void goForward3(){
        splnenyPohyb = false;
        // do metody posielame v podstate rychlost (co je v tomto pripade 100) a cas
        updateMotorControlTime(100, 100, 1);
        // spustenie timeru s parametrom casu, kt. sme  zadali
        new CountDownTimer(time3-1000, 1) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // po skonceni countdowntimeru, posleme zase spravu o "zastaveni" motorov
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb v pred, posielame s parametrom (cas = sekundy)
    public void goForward5(){
        splnenyPohyb = false;
        // do metody posielame v podstate rychlost (co je v tomto pripade 100) a cas
        updateMotorControlTime(100, 100, 1);
        // spustenie timeru s parametrom casu, kt. sme  zadali
        new CountDownTimer(time5-1000, 1) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // po skonceni countdowntimeru, posleme zase spravu o "zastaveni" motorov
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb v pred, posielame s parametrom (cas = sekundy)
    public void goForward10(){
        splnenyPohyb = false;
        // do metody posielame v podstate rychlost (co je v tomto pripade 100) a cas
        updateMotorControlTime(100, 100, 1);
        // spustenie timeru s parametrom casu, kt. sme  zadali
        new CountDownTimer(time10-1000, 1) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // po skonceni countdowntimeru, posleme zase spravu o "zastaveni" motorov
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb vzad, posielame s parametrom (cas=sekundy)
    public void goBackward1(){
        splnenyPohyb = false;
        // to iste ako v tej metode vyssie, zaporne hodnoty pre pohyb vzad
        updateMotorControlTime(-100, -100, 1);
        // a zase timer
        new CountDownTimer(time1-1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            // a zase zastavenie motora po skonceni
            public void onFinish() {
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb vzad, posielame s parametrom (cas=sekundy)
    public void goBackward3(){
        splnenyPohyb = false;
        // to iste ako v tej metode vyssie, zaporne hodnoty pre pohyb vzad
        updateMotorControlTime(-100, -100, 1);
        // a zase timer
        new CountDownTimer(time3-1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            // a zase zastavenie motora po skonceni
            public void onFinish() {
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb vzad, posielame s parametrom (cas=sekundy)
    public void goBackward5(){
        splnenyPohyb = false;
        // to iste ako v tej metode vyssie, zaporne hodnoty pre pohyb vzad
        updateMotorControlTime(-100, -100, 1);
        // a zase timer
        new CountDownTimer(time5-1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            // a zase zastavenie motora po skonceni
            public void onFinish() {
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb vzad, posielame s parametrom (cas=sekundy)
    public void goBackward10(){
        splnenyPohyb = false;
        // to iste ako v tej metode vyssie, zaporne hodnoty pre pohyb vzad
        updateMotorControlTime(-100, -100, 1);
        // a zase timer
        new CountDownTimer(time10-1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            // a zase zastavenie motora po skonceni
            public void onFinish() {
                updateMotorControl(0, 0);
                splnenyPohyb = true;
            }
        }.start(); // spustenie timera
    }

    // metoda na otocenie do lava, parameter zatial neposielame, je to len otocenie
    public void turnLeft(){
        splnenyPohyb = false;
        // poslanie spravy so ziadnym oneskorenim, lavy motor a hodnota 30
        // (najviac mi to sedelo pre otocenie do lava)
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 50, 0);
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, -50, 0);
        // a zase timer, tento krat ale posielame to otocenie aby trvalo 1 sekundu
        // metoda z lega  rotateControl sa dost zasekavala
        new CountDownTimer(time1, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // a zase sprava na zastavenie motora
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 0, 0);
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 0, 0);
                splnenyPohyb = true;
            }
        }.start(); //spustenie timera
    }

    // taka ista metoda ako ta horna, len s inym otocenim motora
    public void turnRight(){
        splnenyPohyb = false;
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, -50, 0);
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 50, 0);
        new CountDownTimer(time1, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 0, 0);
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 0, 0);
                splnenyPohyb = true;
            }
        }.start();
    }

    // metoda na "cakanie"
    // posielame spravu so ziadnym oneskorenim, spravu o cakani a cas v milisekundach
    public void wait1(){
        splneneCakanie = false;
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.WAIT, (1000), 0);
        new CountDownTimer(time1, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                splneneCakanie = true;
            }
        }.start(); //spustenie timera
    }

    // metoda na "cakanie"
    // posielame spravu so ziadnym oneskorenim, spravu o cakani a cas v milisekundach
    public void wait3(){
        splneneCakanie = false;
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.WAIT, (3000), 0);
        new CountDownTimer(time3, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                splneneCakanie = true;
            }
        }.start(); //spustenie timera
    }

    // metoda na "cakanie"
    // posielame spravu so ziadnym oneskorenim, spravu o cakani a cas v milisekundach
    public void wait5(){
        splneneCakanie = false;
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.WAIT, (5000), 0);
        new CountDownTimer(time5, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                splneneCakanie = true;
            }
        }.start(); //spustenie timera
    }

    // metoda na "cakanie"
    // posielame spravu so ziadnym oneskorenim, spravu o cakani a cas v milisekundach
    public void wait10(){
        splneneCakanie = false;
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.WAIT, (10000), 0);
        new CountDownTimer(time10, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                splneneCakanie = true;
            }
        }.start(); //spustenie timera
    }

    // podmienka na splnenie dotyku
    public void dotykMetoda(){
        // nastavime false
        splnenyDotyk = false;

        // spustenie timeru, lebo cakame 5s
        new CountDownTimer(time5, 1) {
            @Override
            // pri ticku sledujeme, ci bol zatlaceny,
            // ak ano dame podmienku ako splnenu
            public void onTick(long l) {
                if(dotyk){
                    splnenyDotyk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyDotyk)
                    // ak nie, vypiseme hlasku
                    showToast(R.string.touchIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    // podmienka na zvuk
    public void soundM25(){
        splnenyZvuk = false;

        // spustime casovac na 5s
        new CountDownTimer(time5, 1) {
            @Override
            // zistime ci hodnota je taka, aku sme zadali z podmienky
            public void onTick(long l) {
                if(currentSoundL <= 25){
                    // vypnutie casovaca
                    this.cancel();
                    // podmienka splnena
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    // vypis hlasky ak nebola splnena
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void soundM50(){
        splnenyZvuk = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentSoundL <= 50){
                    this.cancel();
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void soundM75(){
        splnenyZvuk = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentSoundL <= 75){
                    this.cancel();
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void soundV25(){
        splnenyZvuk = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentSoundL >= 25){
                    this.cancel();
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void soundV50(){
        splnenyZvuk = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentSoundL >= 50){
                    this.cancel();
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void soundV75(){
        splnenyZvuk = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentSoundL >= 75){
                    this.cancel();
                    splnenyZvuk = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyZvuk)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightM25(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL <= 25){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightM50(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL <= 50){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightM75(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL <= 75){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightV25(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL >= 25){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightV50(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL >= 50){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    public void lightV75(){
        splneneSvetlo = false;

        new CountDownTimer(time5, 1) {
            @Override
            public void onTick(long l) {
                if(currentLightL >= 75){
                    this.cancel();
                    splneneSvetlo = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splneneSvetlo)
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    // podmienka na vzdialenost
    public void ultraM25(){
        splnenyUltraZ = false;

        // spustime casovac na 5s
        new CountDownTimer(time5, 1) {
            @Override
            // zistime ci hodnota je taka, aku sme zadali z podmienky
            public void onTick(long l) {
                if(currentUltrasonicL <= 25){
                    // vypnutie casovaca
                    this.cancel();
                    // podmienka splnena
                    splnenyUltraZ = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyUltraZ)
                    // vypis hlasky ak nebola splnena
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    // podmienka na vzdialenost
    public void ultraV25(){
        splnenyUltraZ = false;

        // spustime casovac na 5s
        new CountDownTimer(time5, 1) {
            @Override
            // zistime ci hodnota je taka, aku sme zadali z podmienky
            public void onTick(long l) {
                if(currentUltrasonicL >= 25){
                    // vypnutie casovaca
                    this.cancel();
                    // podmienka splnena
                    splnenyUltraZ = true;
                }
            }
            @Override
            public void onFinish() {
                if (!splnenyUltraZ)
                    // vypis hlasky ak nebola splnena
                    showToast(R.string.reqIncomplete, Toast.LENGTH_SHORT);
            }
        }.start();
    }

    // a teda zacatie hlavnej metody, vykona sa prva metoda a potom sa spusti seria timerov,
    // ktore beru parametre z predoslych metod, na ktorych spustia timer a po skonceni spustia
    // dalsiu metodu, az kym neskoncime
    public void startProgramovanie(View view){
        fieldMethod1();
        new CountDownTimer(casovac1 + 1000, 1) {
            @Override
            public void onTick(long l) {
            }
            @Override
            public void onFinish() {
                if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                    fieldMethod2();
                    new CountDownTimer(casovac2 + 1000, 1) {
                        @Override
                        public void onTick(long l) {
                        }
                        @Override
                        public void onFinish() {
                            if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                                fieldMethod3();
                                new CountDownTimer(casovac3 + 1000, 1) {
                                    @Override
                                    public void onTick(long l) {
                                    }
                                    @Override
                                    public void onFinish() {
                                        if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                                            fieldMethod4();
                                            new CountDownTimer(casovac4 + 1000, 1) {
                                                @Override
                                                public void onTick(long l) {
                                                }
                                                @Override
                                                public void onFinish() {
                                                    if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                                                        fieldMethod5();
                                                        new CountDownTimer(casovac5 + 1000, 1) {
                                                            @Override
                                                            public void onTick(long l) {
                                                            }
                                                            @Override
                                                            public void onFinish() {
                                                                if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                                                                    fieldMethod6();
                                                                    new CountDownTimer(casovac6 + 1000, 1) {
                                                                        @Override
                                                                        public void onTick(long l) {
                                                                        }
                                                                        @Override
                                                                        public void onFinish() {
                                                                            if (splnenyPohyb||splnenyZvuk||splnenyDotyk||splneneSvetlo||splneneCakanie){
                                                                                fieldMethod7();
                                                                            }
                                                                        }
                                                                    }.start();
                                                                }
                                                            }
                                                        }.start();
                                                    }
                                                }
                                            }.start();
                                        }
                                    }
                                }.start();
                            }
                        }
                    }.start();
                }
            }
        }.start();
    }


    // deklarovanie handlerov
    private Handler dHandler = new Handler();
    private Handler bHandler = new Handler();
    private Handler sHandler = new Handler();
    private Handler zHandler = new Handler();
    private Handler uHandler = new Handler();

    // "vlakno" pre dotyk. senzor
    private Runnable dRunnable = new Runnable() {
        @Override
        public void run() {
            // deklarovanie textu
            TextView dotyk = (TextView) findViewById(R.id.touchSenzor);
            // vykonanie metody na zistenie stavu touchu
            udajeTouch();
            // nastavenie textu podla hodnoty
            dotyk.setText(dotykovySenzor);
            // vykonavame kazdych 100 ms
            dHandler.postDelayed(this, 100);
        }
    };

    // "vlakno" pre baterku
    private Runnable bRunnable = new Runnable() {
        @Override
        public void run() {
            // deklarovanie textu
            TextView baterka = (TextView) findViewById(R.id.batterySenzor);
            // poslanie spravy zistenie stavu baterky
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_BATTERY_STATE, 0, 0);
            // nastavenie textu z premennej + %
            baterka.setText(String.valueOf((int)currentMiliVolts) + "%");
            bHandler.postDelayed(this, 5000);
        }
    };

    // "vlakno" pre svetlo
    private Runnable sRunnable = new Runnable() {
        @Override
        public void run() {
            TextView svetlo = (TextView) findViewById(R.id.lightSenzor);
            udajeLight();
            svetelnySenzor = String.valueOf(currentLightL);
            svetlo.setText(svetelnySenzor + "%");
            sHandler.postDelayed(this, 150);
        }
    };

    // "vlakno" pre zvukovy senzor
    private Runnable zRunnable = new Runnable() {
        @Override
        public void run() {
            TextView zvuk = (TextView) findViewById(R.id.soundSenzor);
            udajeSound();
            zvukovySenzor = String.valueOf(currentSoundL);
            if(Integer.parseInt(zvukovySenzor) >= 100)
                zvukovySenzor = String.valueOf(100);
            zvuk.setText(zvukovySenzor + "%");
            zHandler.postDelayed(this, 180);
        }
    };

    // "vlakno" pre ultrazvukovy senzor
    private Runnable uRunnable = new Runnable() {
        @Override
        public void run() {
            TextView ultra = (TextView) findViewById(R.id.ultraSenzor);
            udajeUltrasonic();
            ultrazvukovySenzor = String.valueOf(currentUltrasonicL);
            ultra.setText(ultrazvukovySenzor+" cm");
            uHandler.postDelayed(this, 500);
        }
    };

    // metoda, kt. sa vykona po stlaceni tlacidla na vymazanie poli
    public void deleteAll(View view){
        // deklarovanie tych poli
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        ImageButton field6 = (ImageButton) findViewById(R.id.Field6);
        ImageButton field7 = (ImageButton) findViewById(R.id.Field7);

        // nastavenie hodnot content description na 0
        field1.setContentDescription("0");
        field2.setContentDescription("0");
        field3.setContentDescription("0");
        field4.setContentDescription("0");
        field5.setContentDescription("0");
        field6.setContentDescription("0");
        field7.setContentDescription("0");

        // vymazanie obrazkov
        field1.setImageResource(0);
        field2.setImageResource(0);
        field3.setImageResource(0);
        field4.setImageResource(0);
        field5.setImageResource(0);
        field6.setImageResource(0);
        field7.setImageResource(0);
    }

    // ak klikneme na text o zvuku zmenime co monitoruje dB a dBA
    public void zmenaSoundu(View view){
        TextView zvukText = (TextView) findViewById(R.id.textSound);
        if(!zvukB){
            zvukB = true;
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_SOUND, BTKomunikacia.DB, 0);
            zvukText.setText(getResources().getString(R.string.soundLevel1));
        } else{
            zvukB = false;
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_SOUND, BTKomunikacia.DBA, 0);
            zvukText.setText(getResources().getString(R.string.soundLevel2));
        }
    }

    // ak klikneme na text o svetle zmenime co monitoruje prostredie a odraz svetla
    public void zmenaSvetla(View view){
        TextView svetloText = (TextView) findViewById(R.id.textLight);
        if(!svetloB){
            svetloB = true;
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_LIGHT, BTKomunikacia.LIGHT_ACTIVE, 0);
            svetloText.setText(getResources().getString(R.string.lightLevel1));
        } else{
            svetloB = false;
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.SET_LIGHT, BTKomunikacia.REFLECTION, 0);
            svetloText.setText(getResources().getString(R.string.lightLevel2));
        }
    }
}
