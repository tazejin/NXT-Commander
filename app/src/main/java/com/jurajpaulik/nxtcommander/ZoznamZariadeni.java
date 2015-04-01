package com.jurajpaulik.nxtcommander;

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

// Tato aktivita sa objavuje ako dialog. Vypise vsetky sparovanie zariadenia,
// ktore maju "vzor" ako Lego zariadenia. V pripade ak nie su zariadenia parovane vyhlada dalsie
// zariadenia a mozeme ich sparovat. Po vybrati zariadenia sa posiela MAC addressa spat do hlavnej metody
public class ZoznamZariadeni extends Activity {
    static final String PAROVANIE = "parujem";

    // vracanie udajov naspat
    public static String MENO_A_ADRESA_ZARIADENIA = "nxt_info";
    public static String EXTRA_ADRESA_ZARIADENIA = "adresa_zariadenia";

    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Po spusteni aktivity otvorime okno
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.zoznam_zariadeni);
 
        // Poslanie statusu "ZRUSENE" ak stlacime spat na mobile
        setResult(Activity.RESULT_CANCELED);

        // Zobrazenie buttonu na vyhladanie zariadeni
        Button scanButton = (Button) findViewById(R.id.button_vyhladaj);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        // Ukazanie dvoch zoznamov v dialogu, parovanie zariadenia a vyhladane
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.meno_zariadenia);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.meno_zariadenia);

        // Vyhladanie a zostavenie ListView pre parovane zariadenia
        ListView pairedListView = (ListView) findViewById(R.id.parovane_zariadenia);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // Vyhladanie a zostavenie ListView pre vyhladane zariadenia
        ListView newDevicesListView = (ListView) findViewById(R.id.nove_zariadenia);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // Ziskanie bluetooth adapteru
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Ziskanie listu uz parovanych zariadeni
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // Ak uz mame parovane zariadenia, pridame kazde do ArrayAdapter
        boolean legoDevicesFound = false;
        
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                // Pridanie iba LEGO zariadeni
                if (device.getAddress().startsWith(BTKomunikacia.OUI_LEGO)) {
                    legoDevicesFound = true;
                    mPairedDevicesArrayAdapter.add(device.getName() + "-" + device.getAddress());
                }
            }
        }
        
        if (legoDevicesFound == false) {
            String noDevices = getResources().getText(R.string.ziadne_parovane).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Uistime sa, ze uz nevyhladame zariadenia
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Neregistrovany broadcast listener
        this.unregisterReceiver(mReceiver);
    }

    // Spustenie vyhladavanie cez BluetoothAdapter
    private void doDiscovery() {

        // Ukazeme pouzivatelovi v nadpise, ze skenujeme zariadenia
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.vyhladavanie);

        // Zapnutie podnadpisov pre kazde zariadenie
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // Ak uz sme dohladali, zastavime to
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Vyziadanie vyhladanie pre BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    // Listener  pre kliknutie na zariadenia v ListView
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            String info = ((TextView) v).getText().toString();
            // Kontrola ci bola vybrana spravna adresa a meno
            if (info.lastIndexOf('-') != info.length()-18)
                return;
            // Ked uz klikneme na pripojenie, prestaneme vyhladavat, aby sme nezaplnili stream
            mBtAdapter.cancelDiscovery();
            // Ziskanie MAC adresy zariadenia
            String address = info.substring(info.lastIndexOf('-')+1);
            // Vytvorenie Intentu s vysledkami
            Intent intent = new Intent();
            Bundle data = new Bundle();
            data.putString(MENO_A_ADRESA_ZARIADENIA, info);
            data.putString(EXTRA_ADRESA_ZARIADENIA, address);
            data.putBoolean(PAROVANIE,av.getId()==R.id.nove_zariadenia);
            intent.putExtras(data);
            // Nadstavenie informacii a nasledne zatvorenie tejto aktivity
            setResult(RESULT_OK, intent);
            finish();
        }
    };

    // BroadcastReceiver, ktory nacuva pre vyhladavanie zariadeni a zmeni nadpis pri skonceni
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Ak najdeme zariadenie
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Ziskanie BluetoothDevice objektu z aktivity
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Ak uz je parovany, preskocime aj tak je uz v piste
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "-" + device.getAddress());
                }
            // Ked skoncime vyhladavanie zmenime nadpis
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

}
