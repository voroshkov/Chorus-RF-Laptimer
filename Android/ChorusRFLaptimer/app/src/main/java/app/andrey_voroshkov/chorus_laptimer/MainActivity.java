package app.andrey_voroshkov.chorus_laptimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;

public class MainActivity extends AppCompatActivity implements ConnectionListener {

    private static final int REQUEST_WRITE_STORAGE_CODE = 315;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private Menu menu;
    private BroadcastReceiver mUsbReceiver;
    private PendingIntent mPermissionIntent;
    BTService bt;
    UDPService udp;
    USBService usb;

    public void onDisconnected() {
        Toast.makeText(getApplicationContext(), getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.disconnected);
        AppState.getInstance().onDisconnected();
    }

    public void onConnectionFailed(String errorMsg) {
        AppState.getInstance().conn = null;
        Toast.makeText(getApplicationContext(), getString(R.string.connection_failed), Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.connection_failed);
    }


    public void onConnected(String name) {
        String txt = getString(R.string.connected_to, name);
        Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show();
        AppState.getInstance().speakMessage(R.string.connected);
        AppState.getInstance().onConnected();
    }

    public void onDataReceived(String message) {
        String parsedMsg;
        try {
            parsedMsg = Utils.btDataChunkParser(message);
        } catch (Exception e) {
            parsedMsg = e.toString();
        }
        // Toast.makeText(getApplicationContext(), parsedMsg, Toast.LENGTH_SHORT).show();
    }

    void initUSB() {
        usb = new USBService((UsbManager) getSystemService(Context.USB_SERVICE));
        usb.setConnectionListener(this);
    }


    void initUDP() {
        udp = new UDPService();
        udp.setConnectionListener(this);
    }

    void initBluetooth() {
        bt = new BTService(this, AppState.DELIMITER);

        if (!bt.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "Bluetooth is not available"
                    , Toast.LENGTH_SHORT).show();
            finish();
        }

        bt.setConnectionListener(this);
    }

    void retrieveAndStoreAppVersion (Context context) {
        String version = "";
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        AppState.getInstance().appVersion = version;
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
        if (mViewPager == null) return;

        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        initBluetooth();
        initUDP();
        initBroadcastReceiverForUsbPermissions();
        initUSB();
        retrieveAndStoreAppVersion(getApplicationContext());
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

    public void showWrongApiDialog() {
        String modulesWithWrongApi = AppState.getInstance().getModulesWithWrongApiVersion();
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getResources().getString(R.string.api_err_title))
                .setMessage(getResources().getString(R.string.api_err_message, modulesWithWrongApi, AppState.SUPPORTED_API_VERSION))
                .setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.api_err_button), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (AppState.getInstance().conn != null) {
                            AppState.getInstance().conn.disconnect();
                        }
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
        Connection conn = AppState.getInstance().conn;
        if (conn != null) {
            for(int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setVisible(false);
            }
            menu.findItem(R.id.menuDisconnect).setVisible(true);
        } else {
            UsbDevice device = getAvailableUsbDevice();
            menu.findItem(R.id.menuUSBConnect).setVisible(device != null);
            menu.findItem(R.id.menuBTConnect).setVisible(true);
            menu.findItem(R.id.menuUDPConnect).setVisible(true);
            menu.findItem(R.id.menuDisconnect).setVisible(false);
        }
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
                if (AppState.getInstance().conn != null) {
                    AppState.getInstance().conn.disconnect();
                }
            }
        };

        switch (id) {
            case R.id.menuBTConnect:
                bt.setDeviceTarget(BluetoothState.DEVICE_OTHER);
                Intent intent = new Intent(getApplicationContext(), DeviceList.class);
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
                break;
            case R.id.menuUDPConnect:
                udp.connect(getGatewayIP(), 0);
                useUDP();
                break;
            case R.id.menuUSBConnect:
                checkUSBPermissionsAndConnectIfAllowed();
                break;
            case R.id.menuDisconnect:
                AppState.getInstance().onBeforeDisconnect();
                delayedDisconnect.sendEmptyMessageDelayed(0, 100);
                break;
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
            if (!bt.isServiceAvailable()) {
                bt.runService();
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
        if (file.list() != null) {
            for (int i = 0; i < file.list().length; i++) {
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

    public void useUSB() {
        AppState.getInstance().conn = usb;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if (resultCode == Activity.RESULT_OK) {
                bt.connect(data);
                useBT();
            }
        } else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
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

    private void connectToUsbDevice() {
        usb.connect();
        useUSB();
    }

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private void initBroadcastReceiverForUsbPermissions() {
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                //call method to set up device communication
                                connectToUsbDevice();
                            }
                        } else {
    //                        Log.d(TAG, "permission denied for device " + device);
                            Toast.makeText(getApplicationContext(), getString(R.string.cannotAccessUsbDevice), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        };
    }


    private void checkUSBPermissionsAndConnectIfAllowed() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDevice device = getAvailableUsbDevice();
        if (device == null) return;

        if (manager.hasPermission(device)) {
            connectToUsbDevice();
            return;
        }

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        manager.requestPermission(device, mPermissionIntent);
    }

    private UsbDevice getAvailableUsbDevice() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return null;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        return driver.getDevice();
    }
}
