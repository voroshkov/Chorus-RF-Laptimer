package app.andrey_voroshkov.chorus_laptimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_WRITE_STORAGE_CODE = 315;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private Menu menu;
    BTService bt;
    UDPService udp;

    public void onDisconnected() {
        Toast.makeText(getApplicationContext(), getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.disconnected);
        AppState.getInstance().onDisconnected();
    }

    public void onBTDisconnected() {
        onDisconnected();
        toggleConnectionMenu(false, true);
    }

    public void onUDPDisconnected() {
        onDisconnected();
        toggleConnectionMenu(false, false);
    }

    public void onConnectionFailed() {
        Toast.makeText(getApplicationContext(), getString(R.string.connection_failed), Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.connection_failed);
    }

    public void onBTConnectionFailed() {
        onConnectionFailed();
    }

    public void onUDPConnectionFailed() {
        onConnectionFailed();
    }

    public void onConnected(String name) {
        String txt = getString(R.string.connected_to, name);
        Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.connected);
        AppState.getInstance().onConnected();
    }

    public void onBTConnected(String name) {
        onConnected(name);
        toggleConnectionMenu(true, true);
    }

    public void onUDPConnected(String name) {
        onConnected(name);
        toggleConnectionMenu(true, false);
    }

    public void onDataReceived(String message) {
        String parsedMsg;
        try {
            parsedMsg = Utils.btDataChunkParser(message);
        }
        catch (Exception e) {
            parsedMsg = e.toString();
        }
        // Toast.makeText(getApplicationContext(), parsedMsg, Toast.LENGTH_SHORT).show();
    }

    void initUDP() {
        udp = new UDPService();
        udp.setConnectionListener(new ConnectionListener() {
            @Override
            public void onConnected(String name) {
                MainActivity.this.onUDPConnected(getGatewayIP());
            }

            @Override
            public void onDisconnected() {
                MainActivity.this.onUDPDisconnected();
            }

            @Override
            public void onConnectionFailed(String errorMsg) {
                MainActivity.this.onUDPConnectionFailed();
            }

            @Override
            public void onDataReceived(String message) {
                MainActivity.this.onDataReceived(message);
            }
        });
    }

    void initBluetooth() {
        bt = new BTService(this, AppState.DELIMITER);

        if(!bt.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "Bluetooth is not available"
                    , Toast.LENGTH_SHORT).show();
            finish();
        }

        bt.setConnectionListener(new ConnectionListener() {
            public void onDisconnected() {
                MainActivity.this.onBTDisconnected();
            }

            public void onConnectionFailed(String s) {
                MainActivity.this.onBTConnectionFailed();            }

            public void onConnected(String name) {
                MainActivity.this.onBTConnected(name);
            }

            public void onDataReceived(String msg) {
                MainActivity.this.onDataReceived(msg);
            }
        });
    }

    void toggleConnectionMenu(boolean isConnected, boolean isBluetooth) {
        menu.findItem(R.id.menuBTConnect).setVisible(!isConnected);
        menu.findItem(R.id.menuUDPConnect).setVisible(!isConnected);
        boolean isBtConnected = isConnected && isBluetooth;
        menu.findItem(R.id.menuBTDisconnect).setVisible(isBtConnected);
        boolean isUdpConnected = isConnected && !isBluetooth;
        menu.findItem(R.id.menuUDPDisconnect).setVisible(isUdpConnected);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        /*
          The {@link android.support.v4.view.PagerAdapter} that will provide
        fragments for each of the sections. We use a
        {@link FragmentPagerAdapter} derivative, which will keep every
        loaded fragment in memory. If this becomes too memory intensive, it
        may be best to switch to a
        {@link android.support.v4.app.FragmentStatePagerAdapter}.
         */
        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), getResources());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        initBluetooth();
        initUDP();
        AppState.getInstance().textSpeaker = new TextSpeaker(getApplicationContext(),
                AppState.getInstance().shouldSpeakEnglishOnly);
        AppState.getInstance().preferences = getPreferences(MODE_PRIVATE);
        AppPreferences.applyAll();

        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case WrongApiVersion:
                        MainActivity.this.showWrongApiDialog();
                }
            }
        });
        //Ensure permissions permissions before any disk IO
        ensurePermissions();
        //this will cleanup csv reports after 2 weeks (14 days)
        cleanUpCSVReports();
    }

    public void showWrongApiDialog () {
        String modulesWithWrongApi = AppState.getInstance().getModulesWithWrongApiVersion();
        new AlertDialog.Builder(MainActivity.this)
            .setTitle(getResources().getString(R.string.api_err_title))
            .setMessage(getResources().getString(R.string.api_err_message, modulesWithWrongApi, AppState.SUPPORTED_API_VERSION))
            .setCancelable(false)
            .setPositiveButton(getResources().getString(R.string.api_err_button), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AppState.getInstance().conn.disconnect();
                }
            }).show();
    }

    public void onDestroy() {
        super.onDestroy();
        bt.stopService();
        AppState.getInstance().textSpeaker.shutdown();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Handler delayedDisconnect = new Handler() {
            public void handleMessage(Message msg) {
                AppState.getInstance().conn.disconnect();
            }
        };

        if(id == R.id.menuBTConnect) {
            bt.setDeviceTarget(BluetoothState.DEVICE_OTHER);
            Intent intent = new Intent(getApplicationContext(), DeviceList.class);
            startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
        } else if(id == R.id.menuBTDisconnect) {
            if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
                AppState.getInstance().onBeforeDisconnect();
                delayedDisconnect.sendEmptyMessageDelayed(0, 100);
            }
        } else if(id == R.id.menuUDPConnect) {
            udp.connect(getGatewayIP(), 0);
            useUDP();
        } else if(id == R.id.menuUDPDisconnect) {
            AppState.getInstance().onBeforeDisconnect();
            delayedDisconnect.sendEmptyMessageDelayed(0, 100);
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkIsWifiOnAndConnected() {
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wifiMgr.isWifiEnabled()) return false;  // Wi-Fi adapter is OFF

        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        return wifiInfo.getNetworkId() != -1; // if true, then connected to an access point
    }

    private String getGatewayIP() {
        if (!checkIsWifiOnAndConnected()) return "0.0.0.0";

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        int ip = dhcp.gateway;
        return String.format("%d.%d.%d.%d",
            (ip & 0xff),
            (ip >> 8 & 0xff),
            (ip >> 16 & 0xff),
            (ip >> 24 & 0xff)
        );
    }

    public void onStart() {
        super.onStart();
        if (!bt.isBluetoothEnabled()) {
//            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
        } else {
            if(!bt.isServiceAvailable()) {
                bt.runService();
                useBT();
            }
        }

    }

    /**
     * This function will cleanUp CSV Reports if it has been 2 weeks since file is last updated.
     */
    public void cleanUpCSVReports() {
        //use ChorusLapTimer directory
        String path = Utils.getReportPath();
        File file = new File(path);

        //get date today
        //TODO: check if it really works with Calendar, or use Date?
        Calendar calToday = Calendar.getInstance();
        long todayMillis = calToday.getTimeInMillis();

        //iterate from files inside the ChorusLapTimer directory
        if(file.list() != null){
            for(int i = 0; i < file.list().length; i++){
                File currFile = file.listFiles()[i];
                //check difference of file.lastModified compared to date today
                long diff = todayMillis - currFile.lastModified();
                //convert difference to number of days
                long numDays = TimeUnit.MILLISECONDS.toDays(diff);
                //if number of days are 14(2 weeks), delete the file
                if (numDays > 14) {
                    try {
                        currFile.delete();
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
    }

    public void useBT() {
        AppState.getInstance().conn = bt;
    }

    public void useUDP() {
        AppState.getInstance().conn = udp;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK)
                bt.connect(data);
                useBT();
        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                bt.runService();
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void ensurePermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_CODE);
        }
    }
}
