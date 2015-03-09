package com.jurajpaulik.legonxt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;

//TODO prehadzovanie menu, na mobile SGS4 to ide ale pri tabletoch sa cely system zmenil a musim pridat actionbar
//TODO po zmene menu by sa mali spravne vypichovat medzi sebou
//TODO prist na nejake ine metody a optimalizaciu niektorych zlych metod
//TODO treba najst sposob ako citat spravne udaje zo senzorov a pridat ich do programovania (ak bude cas) + monitorovanie
//TODO opravenie toho vypinania BT
//TODO pri zvoleni zatocenia odstranit pole nazadanie casu
// Tato trieda sluzi na komunikaciu s LEGO NXT robotom a ovladanim ho cez bluetooth
// Komunikacia s robotom prebieha cez LCP (Lego Communication protocol)
public class Main extends Activity implements BTPripojenie {

    public static final int MENU_TOGGLE_CONNECT = Menu.FIRST;
    public static final int MENU_PROGRAMOVANIE = Menu.FIRST + 1;
    public static final int MENU_CONTROLS = Menu.FIRST + 2;
    public static final int MENU_START_SW = Menu.FIRST + 3;
    public static final int MENU_START_SOUND = Menu.FIRST + 4;

    public static final int REQUEST_CONNECT_DEVICE = 1000;
    public static final int REQUEST_ENABLE_BT = 2000;
    public BTKomunikacia myBTKomunikacia = null;
    public boolean connected = false;
    public ProgressDialog connectingProgressDialog;
    public Handler btcHandler;
    public Menu myMenu;
    public Activity thisActivity;
    public boolean btErrorPending = false;
    public boolean pairing;
    public static boolean btzApp = false;
    int motorLeft;
    public int directionLeft;
    int motorRight;
    public boolean stopAlreadySent = false;
    public int directionRight; // +/- 1
    public List<String> programList;
    public List<String> soundList;
    public static final int MAX_PROGRAMS = 20;
    public String programToStart;
    public Toast reusableToast;
    public int power = 100;
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


    // Ak bol BT zapnuty pocas nasej aplikacie, tak po jej ukonceni ho aj vypneme
    public static boolean BTcezAPP() {
        return btzApp;
    }

    // sme zapli BT tak nam ho flagne
    public static void appBT(boolean btOnByUs) {
        Main.btzApp = btOnByUs;
    }

    // ak aplikacia sa momentalne paruje so zariadenim, vratime true
    @Override
    public boolean isPairing() {
        return pairing;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisActivity = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // nadstavenie na nase hlavne okno / ovladanie
        setContentView(R.layout.ovladanie);
        super.onStart();
        reusableToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        // nadstavenie motorov pri klasickom zapojeni
        motorLeft = BTKomunikacia.MOTOR_B;
        directionLeft = 1;
        motorRight = BTKomunikacia.MOTOR_C;
        directionRight = 1;

        // nadstavenie hodnot pre plynule ovladanie, podla parametrov sa generuju data posielane
        // do nxt kocky, ktora podla toho otaca motor
        // motor berie hodnotu od -100 do 100
        // hodnoty su uvedene nizsie, podrobnosti v metode na ovladanie
        // 0.5 pre polovicne otocenie, cize do prava alebo do lava
        ImageButton buttonUp = (ImageButton) findViewById(R.id.buttonUp);
        buttonUp.setOnTouchListener(new DirectionButtonOnTouchListener(1, 1));
        ImageButton buttonLeft = (ImageButton) findViewById(R.id.buttonLeft);
        buttonLeft.setOnTouchListener(new DirectionButtonOnTouchListener(0.5, -0.5));
        ImageButton buttonDown = (ImageButton) findViewById(R.id.buttonDown);
        buttonDown.setOnTouchListener(new DirectionButtonOnTouchListener(-1, -1));
        ImageButton buttonRight = (ImageButton) findViewById(R.id.buttonRight);
        buttonRight.setOnTouchListener(new DirectionButtonOnTouchListener(-0.5, 0.5));

        // deklarovanie SeekBaru na indikaciu "rychlosti"
        final SeekBar powerSeekBar = (SeekBar) findViewById(R.id.power_seekbar);
        powerSeekBar.setProgress(power);
        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                // ak pri zmene je SeekBar na mensej hodnote nez 25, tak ju nadstavime na 25
                // je to hodnota, pri ktorej sa robot aspon trochu pohne
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

    // ak sa pripojime k robotovi tak inicializujeme a updatneme menu
    public void updateButtonsAndMenu() {
        if (myMenu == null)
            return;

        myMenu.removeItem(MENU_TOGGLE_CONNECT);
        myMenu.removeItem(MENU_CONTROLS);
        if (connected) {
            myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.disconnect));
        } else {
            myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.connect));
        }
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
        updateButtonsAndMenu();
    }

    // poslanie spravy na ukoncenie vlakna
    public void destroyBTCommunicator() {

        if (myBTKomunikacia != null) {
            sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.CLOSE, 0, 0);
            myBTKomunikacia = null;
        }

        connected = false;
        updateButtonsAndMenu();
    }

    // vratenie aktualneho stavu pripojenia k robotovi
    //public boolean isConnected() {
     //   return connected;
   // }

    // spustenie programu priamo v kocke
    public void startProgram(String name) {
        // spustenie programu, ktory sme nasli v kocke
        // .rxe su kompilovane programy, kt sme presunuli do kocky cez NXT-G
        // .rso su zvukove subory a .rpg su subory vytvorene v kocke pomocou "NXT PROGRAM" menu
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.START_PROGRAM, name);
    }

    // Nadstavenie listeneru pre tlacidla, ktore su v layout.ovladanie
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

    // Posielanie parametrov do tejto metody, ktora ju posle dalej na komunikaciu do nxt kocky
    public void updateMotorControl(double left, double right) {

        if (myBTKomunikacia != null) {
            // ak je hodnota 0, tak sa robot nehybe
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
            showToast(R.string.sensor_initialization_failure, Toast.LENGTH_LONG);
            //destroyBTCommunicator();
            finish();
        }
    }

    // metoda, ktora sa spusti pri starte aktivity
    @Override
    protected void onStart() {
        super.onStart();

        // ak nemame zapnuty BT, tak ho zapneme
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

    // metoda pri skonceni aktivity
    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyBTCommunicator();
    }

    // pri pozastaveni aktivity ...
    @Override
    public void onPause() {
        //destroyBTCommunicator();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
    }

    // Vytvorenie menu a pridani itemov do vnutra
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myMenu = menu;
        myMenu.add(0, MENU_TOGGLE_CONNECT, 1, getResources().getString(R.string.connect));
        myMenu.add(0, MENU_PROGRAMOVANIE, 2, getResources().getString(R.string.programovanie));
        myMenu.add(0, MENU_CONTROLS, 3, getResources().getString(R.string.ovladanie));
        myMenu.add(0, MENU_START_SW, 4, getResources().getString(R.string.start));
        myMenu.add(0, MENU_START_SOUND, 5, getResources().getString(R.string.sound));
        updateButtonsAndMenu();
        return true;
    }


    // Vypinanie a zapinanie menu itemov
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean displayMenu;
        displayMenu = super.onPrepareOptionsMenu(menu);
        if (displayMenu) {
            boolean startEnabled = false;
            if (myBTKomunikacia != null)
                startEnabled = myBTKomunikacia.isConnected();
            menu.findItem(MENU_START_SW).setEnabled(startEnabled);
            menu.findItem(MENU_START_SOUND).setEnabled(startEnabled);
        }
        return displayMenu;
    }

    // handler pre stlacenie menu itemov
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // po stlaceni connect/disconnect spravime metody, kt. su spiate s nazvom
            case MENU_TOGGLE_CONNECT:
                if (myBTKomunikacia == null || !connected) {
                    selectNXT();
                } else {
                    destroyBTCommunicator();
                    updateButtonsAndMenu();
                }
                return true;
            // po kliknuti na ovladani zmizne z menu a objavi sa programovanie, updatneme menu
            // a nadstavime content view na ovladanie
            case MENU_CONTROLS:
                setContentView(R.layout.ovladanie);
                return true;
            // po kliknuti na programovanie zmizne z menu, objavi sa ovladanie, updatneme menu
            // a nadstavime content view na programovanie
            case MENU_PROGRAMOVANIE:
                setContentView(R.layout.programovanie);
                return true;
            // po kliknuti na spustenie programu vypiseme hlasku ak nenajdeme subory
            // inak ich ukazeme v dialogu
            case MENU_START_SW:
                if (programList.size() == 0) {
                    showToast(R.string.no_programs_found, Toast.LENGTH_SHORT);
                    break;
                }
                Subory mySubory = new Subory(this, programList);
                mySubory.show();
                return true;
            // po kliknuti na prehranie zvuku vypiseme hlasku ak nejajdeme ziadne
            // inak ich ukazeme v dialogu
            case MENU_START_SOUND:
                if (soundList.size() == 0) {
                    showToast(R.string.no_sound_found, Toast.LENGTH_SHORT);
                    break;
                }
                Subory mySounds = new Subory(this, soundList);
                mySounds.show();
                return true;
        }
        return false;
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
                // ak sme pripojeny, nadstavime hodnotu, zobrazime zoznam zvukov a programov,
                // updatneme menu a pripojovaci dialog dame prec
                // na zistenie suborov posleme get_firmware_version, kt. zisti subory
                case BTKomunikacia.STATE_CONNECTED:
                    connected = true;
                    programList = new ArrayList<>();
                    soundList = new ArrayList<>();
                    connectingProgressDialog.dismiss();
                    updateButtonsAndMenu();
                    sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.GET_FIRMWARE_VERSION, 0, 0);
                    sendBTCmessage(BTKomunikacia.SHORT_DELAY, BTKomunikacia.GET_BATTERY_STATE, 0, 0);
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
                        // nasledne prehladame robota a vsetky subory v nom
                        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.FIND_FILES, 0, 0);
                    }
                    break;
                // zistenie informacii o robotovi (meno, BT adresa, BT signal, volna pamat)
                case BTKomunikacia.GET_DEVICE_INFO:
                    byte[] deviceMessage = myBTKomunikacia.getReturnMessage();
                    String deviceStr = new String(deviceMessage);
                    deviceStr = deviceStr.replaceAll("\0", "");
                    break;
                // najdene suborov
                case BTKomunikacia.FIND_FILES:
                    // ak sme pripojeny
                    if (myBTKomunikacia != null) {
                        byte[] fileMessage = myBTKomunikacia.getReturnMessage();
                        String fileName = new String(fileMessage, 4, 20);
                        fileName = fileName.replaceAll("\0","");

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
                case BTKomunikacia.GET_BATTERY_STATE:
                    if (myBTKomunikacia != null){
                        byte[] batteryMessage = myBTKomunikacia.getReturnMessage();
                        String batteryStr = new String(batteryMessage);
                        break;
                    }
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
                        btzApp = true;
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

    // po vytvoreni context menu
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        // naplnime ho
        super.onCreateContextMenu(menu, view, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_programovanie, menu);
        buttonID = view.getId();

        command1 = true; command2 = true; command3 = true; command4 = true; command5 = true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);

        // zistenie na kazdom buttone, na co sme klikli a podla toho nadstavime "ikonu"
        // a nadstavime flag v podobe cisla, kt. neskor pouzijeme na zistenie metody na vykonanie
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
                case R.id.wait:
                    field1.setImageResource(R.drawable.wait);
                    field1.setContentDescription("5");
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
                case R.id.wait:
                    field2.setImageResource(R.drawable.wait);
                    field2.setContentDescription("5");
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
                case R.id.wait:
                    field3.setImageResource(R.drawable.wait);
                    field3.setContentDescription("5");
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
                case R.id.wait:
                    field4.setImageResource(R.drawable.wait);
                    field4.setContentDescription("5");
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
                case R.id.wait:
                    field5.setImageResource(R.drawable.wait);
                    field5.setContentDescription("5");
                    break;
                default:
                    return super.onContextItemSelected(item);
            }
        }
        return true;
    }

    // metoda pre prve pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktory zistime z EditTextu s pod pola
    // parameter je cas, ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod1(){
        ImageButton field1 = (ImageButton) findViewById(R.id.Field1);
        EditText parameter1 = (EditText) findViewById(R.id.par1);
        param1 = Integer.parseInt(String.valueOf(parameter1.getText()));
        if(field1.getContentDescription() == "1"){
            turnLeft(param1);
        } else if (field1.getContentDescription() == "2"){
            turnRight(param1);
        } else if (field1.getContentDescription() == "3"){
            goForward(param1);
        } else if (field1.getContentDescription() == "4"){
            goBackward(param1);
        } else if (field1.getContentDescription() == "5"){
            waitProgram(param1);
        }
    }

    // metoda pre druhe pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktory zistime z EditTextu s pod pola
    // parameter je cas, ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod2(){
        ImageButton field2 = (ImageButton) findViewById(R.id.Field2);
        EditText parameter2 = (EditText) findViewById(R.id.par2);
        param2 = Integer.parseInt(String.valueOf(parameter2.getText()));
        if(field2.getContentDescription() == "1"){
            turnLeft(param2);
        } else if (field2.getContentDescription() == "2"){
            turnRight(param2);
        } else if (field2.getContentDescription() == "3"){
            goForward(param2);
        } else if (field2.getContentDescription() == "4"){
            goBackward(param2);
        } else if (field2.getContentDescription() == "5"){
            waitProgram(param2);
        }
    }

    // metoda pre tretie pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktory zistime z EditTextu s pod pola
    // parameter je cas, ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod3(){
        ImageButton field3 = (ImageButton) findViewById(R.id.Field3);
        EditText parameter3 = (EditText) findViewById(R.id.par3);
        param3 = Integer.parseInt(String.valueOf(parameter3.getText()));
        if(field3.getContentDescription() == "1"){
            turnLeft(param3);
        } else if (field3.getContentDescription() == "2"){
            turnRight(param3);
        } else if (field3.getContentDescription() == "3"){
            goForward(param3);
        } else if (field3.getContentDescription() == "4"){
            goBackward(param3);
        } else if (field3.getContentDescription() == "5"){
            waitProgram(param3);
        }
    }

    // metoda pre stvrte pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktory zistime z EditTextu s pod pola
    // parameter je cas, ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod4(){
        ImageButton field4 = (ImageButton) findViewById(R.id.Field4);
        EditText parameter4 = (EditText) findViewById(R.id.par4);
        param4 = Integer.parseInt(String.valueOf(parameter4.getText()));
        if(field4.getContentDescription() == "1"){
            turnLeft(param4);
        } else if (field4.getContentDescription() == "2"){
            turnRight(param4);
        } else if (field4.getContentDescription() == "3"){
            goForward(param4);
        } else if (field4.getContentDescription() == "4"){
            goBackward(param4);
        } else if (field4.getContentDescription() == "5"){
            waitProgram(param4);
        }
    }

    // metoda pre piate pole, zistime aky ma pouzity flag, podla toho vykoname metodu,
    // ktoru posleme s parametrom, ktory zistime z EditTextu s pod pola
    // parameter je cas, ako dlho bude vykonavanie metody trvat (v sekundach)
    public void fieldMethod5(){
        ImageButton field5 = (ImageButton) findViewById(R.id.Field5);
        EditText parameter5 = (EditText) findViewById(R.id.par5);
        param5 = Integer.parseInt(String.valueOf(parameter5.getText()));
        if(field5.getContentDescription() == "1"){
            turnLeft(param5);
        } else if (field5.getContentDescription() == "2"){
            turnRight(param5);
        } else if (field5.getContentDescription() == "3"){
            goForward(param5);
        } else if (field5.getContentDescription() == "4"){
            goBackward(param5);
        } else if (field5.getContentDescription() == "5"){
            waitProgram(param5);
        }
    }

    // metoda na pohyb v pred, posielame s parametrom (cas = sekundy)
    public void goForward(int param){
        // do metody posielame v podstate rychlost (co je v tomto pripade 100) a cas
        updateMotorControlTime(100, 100, param);
        // spustenie timeru s parametrom casu, kt. sme  zadali
        new CountDownTimer(param * 1000 - 1000, 1) {
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // po skonceni countdowntimeru, posleme zase spravu o "zastaveni" motorov
                updateMotorControl(0, 0);
            }
        }.start(); // spustenie timera
    }

    // metoda na pohyb vzad, posielame s parametrom (cas=sekundy)
    public void goBackward(int param){
        // to iste ako v tej metode vyssie, zaporne hodnoty pre pohyb vzad
        updateMotorControlTime(-100, -100, param);
        // a zase timer
        new CountDownTimer(param * 1000 - 1000, 1){
            public void onTick(long millisUntilFinished) {
            }
            // a zase zastavenie motora po skonceni
            public void onFinish() {
                updateMotorControl(0, 0);
            }
        }.start(); // spustenie timera
    }

    // metoda na otocenie do lava, parameter zatial neposielame, je to len otocenie
    public void turnLeft(int param){
        // poslanie spravy so ziadnym oneskorenim, lavy motor a hodnota 30
        // (najviac mi to sedelo pre otocenie do lava)
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 30, 0);
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, -30, 0);
        // a zase timer, tento krat ale posielame to otocenie aby trvalo 1 sekundu
        // metoda z lega  rotateControl sa dost zasekavala
        new CountDownTimer(1001, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                // a zase sprava na zastavenie motora
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 0, 0);
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 0, 0);
            }
        }.start(); //spustenie timera
    }

    // taka ista metoda ako ta horna, len s inym otocenim motora
    public void turnRight(int param){
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, -30, 0);
        sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 30, 0);
        new CountDownTimer(1001, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorLeft, 0, 0);
                sendBTCmessage(BTKomunikacia.NO_DELAY, motorRight, 0, 0);
            }
        }.start();
    }

    // metoda na "cakanie"
    // posielame spravu so ziadnym oneskorenim, spravu o cakani a cas v milisekundach
    public void waitProgram(int param){
        sendBTCmessage(BTKomunikacia.NO_DELAY, BTKomunikacia.WAIT, (param * 1000), 0);
    }

    // a teda zacatie hlavnej metody, vykona sa prva metoda a potom sa spusti seria timerov,
    // ktore beru parametre z predoslych metod, na ktorych spustia timer a po skonceni spustia
    // dalsiu metodu, az kym neskoncime
    public void startProgramovanie(View view){
        fieldMethod1();
        new CountDownTimer(param1 * 1001, 1){
            public void onTick(long millisUntilFinished) {
            }
            public void onFinish() {
                fieldMethod2();
                new CountDownTimer(param2 * 1001, 1){
                    public void onTick(long millisUntilFinished) {
                    }
                    public void onFinish() {
                        fieldMethod3();
                        new CountDownTimer(param3 * 1001, 1){
                            public void onTick(long millisUntilFinished) {
                            }
                            public void onFinish() {
                                fieldMethod4();
                                new CountDownTimer(param4 * 1001, 1){
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
}