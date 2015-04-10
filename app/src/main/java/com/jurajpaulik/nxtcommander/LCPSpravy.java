package com.jurajpaulik.nxtcommander;

// Trieda na uchovavanie LCP sprav (aby sme si nemuseli pamatat vsetky bajtove polia)
public class LCPSpravy {

    // Prvy bajt posielaneho pola, indikuje o aky typ spravy sa jedna
    public static byte DIRECT_COMMAND_REPLY = 0x00; // priama sprava s odpovedou
    public static byte SYSTEM_COMMAND_REPLY = 0x01; // systemova sprava s opovedou
    public static byte REPLY = 0x02; // odpoved od nxt
    public static byte DIRECT_COMMAND_NOREPLY = (byte)0x80; // priama sprava bez odpovede

    // Priame prikazy posielane do NXT kocky
    // Priame prikazy sluzia na komunikaciu so senzormi a motormi
    public static final byte START_PROGRAM = 0x00; // spusti program
    public static final byte PLAY_TONE = 0x03; // prehra ton
    public static final byte SET_OUTPUT_STATE = 0x04; // nadstavenie vystupneho stavu
    public static final byte GET_OUTPUT_STATE = 0x06; // ziskanie vystupneho stavu
    public static final byte RESET_MOTOR_POSITION = 0x0A; // resetovanie pozicie motora
    public static final byte GET_BATTERY_STATE = 0x0B; // ziskanie stavu baterie
    public static final byte GET_CURRENT_PROGRAM_NAME = 0x11; // ziskanie nazvu beziaceho programu
    public static final byte GET_INPUT_MODE = 0x07; // ziskanie udajov o senzoroch spat
    public static final byte SET_INPUT_MODE = 0x05; // nastavenie senzorov
    public static final int PORT0_TOUCH = 0; // port 1 - touch
    public static final int PORT1_SOUND = 1; // port 2 - zvukovy
    public static final int PORT2_LIGHT = 2; // port 3 - svetelny
    public static final int PORT3_ULTRASONIC = 3; // port 4 - ultrazvukovy
    public static final byte LS_WRITE = 0x0F;
    public static final byte LS_READ = 0x10;

    // Systemove prikazy, ktore riadia kocku
    public static final byte FIND_FIRST = (byte)0x86; // najdi prvy (subor)
    public static final byte FIND_NEXT = (byte)0x87; // najdi dalsi (subor)
    public static final byte GET_FIRMWARE_VERSION = (byte)0x88; // ziskanie firmware verzie
    public static final byte GET_DEVICE_INFO = (byte)0x9B; // ziskanie informacii o nxt

    // zahranie tonu cez robota o urcitej frekvencii a dlzke
    public static byte[] getBeepMessage(int frequency, int duration) {
        byte[] message = new byte[6];

        message[0] = DIRECT_COMMAND_NOREPLY;
        message[1] = PLAY_TONE;
        // Frekvencia pre ton, Hz (UWORD); Rozpatie: 200-14000 Hz
        message[2] = (byte) frequency;
        message[3] = (byte) (frequency >> 8);
        // Dlzka tonu v ms (UWORD)
        message[4] = (byte) duration;
        message[5] = (byte) (duration >> 8);

        return message;
    }

    // Ziskanie spravy z motorov
    public static byte[] getMotorMessage(int motor, int speed) {
        byte[] message = new byte[12];

        message[0] = DIRECT_COMMAND_NOREPLY;
        message[1] = SET_OUTPUT_STATE;
        // Vystupny port
        message[2] = (byte) motor;
        if (speed == 0) {
            message[3] = 0;
            message[4] = 0;
            message[5] = 0;
            message[6] = 0;
            message[7] = 0;
        } else {
            // Rychlost od -100 do 100
            message[3] = (byte) speed;
            message[4] = 0x03;
            // Mod regulacie motora (reguluje rychlost)
            message[5] = 0x01;
            // Otocenie motora od -100 do 100
            message[6] = 0x00;
            // Stav motora, naspat pride sprava MOTOR_RUN_STATE_RUNNING (Motor bezi)
            message[7] = 0x20;
        }
        // TachoLimit: chod do nekonecna
        message[8] = 0;
        message[9] = 0;
        message[10] = 0;
        message[11] = 0;

        return message;
    }

    // Zistenie spravy z motora
    public static byte[] getMotorMessage(int motor, int speed, int end) {
        byte[] message = getMotorMessage(motor, speed);

        message[8] = (byte) end;
        message[9] = (byte) (end >> 8);
        message[10] = (byte) (end >> 16);
        message[11] = (byte) (end >> 24);

        return message;
    }

    // Zistenie spravy o resetovani
    public static byte[] getResetMessage(int motor) {
        byte[] message = new byte[4];

        message[0] = DIRECT_COMMAND_NOREPLY;
        message[1] = RESET_MOTOR_POSITION;
        // Vystupny port
        message[2] = (byte) motor;
        // Absolutna pozicia
        message[3] = 0;

        return message;
    }

    // Ziskanie spravy o spusteni programu
    public static byte[] getStartProgramMessage(String programName) {
        byte[] message = new byte[22];

        message[0] = DIRECT_COMMAND_NOREPLY;
        message[1] = START_PROGRAM;

        // skopirovanie nazvu programu a zakoncime nulou
        for (int pos=0; pos<programName.length(); pos++)
            message[2+pos] = (byte) programName.charAt(pos);

        message[programName.length()+2] = 0;

        return message;
    }

    // Ziskanie spravy o nazvu programu
    public static byte[] getProgramNameMessage() {
        byte[] message = new byte[2];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_CURRENT_PROGRAM_NAME;

        return message;
    }

    // Ziskanie spravy o vystupnom stave motora
    public static byte[] getOutputStateMessage(int motor) {
        byte[] message = new byte[3];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_OUTPUT_STATE;
        // Vystupny port motora
        message[2] = (byte) motor;

        return message;
    }

    // Ziskanie spravy o firmware verzii
    public static byte[] getFirmwareVersionMessage() {
        byte[] message = new byte[2];

        message[0] = SYSTEM_COMMAND_REPLY;
        message[1] = GET_FIRMWARE_VERSION;

        return message;
    }

    // Ziskanie spravy o hladine baterky
    public static byte[] getBatteryLevelMessage(){
        byte[] message = new byte[2];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_BATTERY_STATE;

        return message;
    }

    // Ziskanie spravy o informacii ohladom NXT kocky (meno, BT adresa, BT sila signalu, volna pamat ...)
    public static byte[] getDeviceInfoMessage(){
        byte[] message = new byte[20];

        message[0] = SYSTEM_COMMAND_REPLY;
        message[1] = GET_DEVICE_INFO;

        return message;
    }

    // Vratenie spravy ohladom najdenych suborov v kocke
    public static byte[] getFindFilesMessage(boolean findFirst, int handle, String searchString) {
        byte[] message;

        if (findFirst)
            message = new byte[22];
        else
            message = new byte[3];

        message[0] = SYSTEM_COMMAND_REPLY;

        if (findFirst) {
            message[1] = FIND_FIRST;
            // skopirujeme vyhladavany nazov a zakoncime nulou
            for (int pos=0; pos<searchString.length(); pos++)
                message[2+pos] = (byte) searchString.charAt(pos);

            message[searchString.length()+2] = 0;

        } else {
            // hladame dalej
            message[1] = FIND_NEXT;
            message[2] = (byte) handle;
        }
        return message;
    }

    // sprava o ziskani udajov z dotykoveho senzora
    public static byte[] getTouchMessage(){
        byte[] message = new byte[3];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_INPUT_MODE;
        message[2] = (byte) PORT0_TOUCH;

        return message;
    }

    // sprava o ziskani udajov zo zvukoveho senzora
    public static byte[] getSoundMessage(){
        byte[] message = new byte[3];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_INPUT_MODE;
        message[2] = (byte) PORT1_SOUND;

        return message;
    }

    // sprava o ziskani udajov zo svetelneho senzora
    public static byte[] getLightMessage(){
        byte[] message = new byte[3];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_INPUT_MODE;
        message[2] = (byte) PORT2_LIGHT;

        return message;
    }

    // sprava o ziskani udajov z ultrazvukoveho senzora
    public static byte[] getUltrasonicMessage(){
        byte[] message = new byte[3];

        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = GET_INPUT_MODE;
        message[2] = (byte) PORT3_ULTRASONIC;

        return message;
    }

    // nastavenie svetelneho senzora
    public static byte[] setLightMessage(int type){
        byte[] message = new byte[5];
        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = SET_INPUT_MODE;
        message[2] = (byte) PORT2_LIGHT;

        // ak posleme zo spravu aj parameter hodnoty - typ senzora
        //0 tak je svetelny senzor neaktivny
        if(type == 0)
            message[3] = (byte) 0x06;
        // 1 tak meriame prostredie
        else if (type == 1)
            message[3] = (byte) 0x03;
        // 2 tak meriame odraz svetla
        else if (type == 2)
            message[3] = (byte) 0x05;
        message[4] = (byte) 0x00; // raw mode

        return message;
    }

    // nastavenie zvukoveho senzora
    public static byte[] setSoundMessage(int type){
        byte[] message = new byte[5];
        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = SET_INPUT_MODE;
        message[2] = (byte) PORT1_SOUND;

        // podla toho aky parameter posleme zo spravou tak merame (typ senzora)
        // pri 0 DB
        if(type == 0)
            message[3] = (byte) 0x07;
        // pri 1 DBA
        else if (type == 1)
            message[3] = (byte) 0x08;
        message[4] = (byte) 0x00; // mod senzora - raw mode

        return message;
    }

    // nastavenie ultrazvukoveho senzora
    public static byte[] setUltraMessage(){
        byte[] message = new byte[5];
        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = SET_INPUT_MODE;
        message[2] = (byte) PORT3_ULTRASONIC;
        message[3] = (byte) 0x0A;
        message[4] = (byte) 0x00;

        return message;
    }

    // nastavenie dotykoveho senzora
    public static byte[] setTouchMessage(){
        byte[] message = new byte[5];
        message[0] = DIRECT_COMMAND_REPLY; // priamy prikaz a chceme odpoved
        message[1] = SET_INPUT_MODE; // nastavenie senzora
        message[2] = (byte) PORT0_TOUCH; // port 1 (na kocke) - dotykovy
        message[3] = (byte) 0x01; // typ senzora - switch
        message[4] = (byte) 0x20; // mod senzora - boolean

        return message;
    }

    public static byte[] LSWriteMessage(){
        byte[] message = new byte[5];
        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = LS_WRITE;
        message[2] = (byte) PORT3_ULTRASONIC;
        message[3] = 16;
        message[4] = 16;

        return message;
    }

    public static byte[] LSReadMessage(){
        byte[] message = new byte[3];
        message[0] = DIRECT_COMMAND_REPLY;
        message[1] = LS_READ;
        message[2] = (byte) PORT3_ULTRASONIC;

        return message;
    }
}
