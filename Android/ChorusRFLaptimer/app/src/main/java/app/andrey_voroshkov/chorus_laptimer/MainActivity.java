package app.andrey_voroshkov.chorus_laptimer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;

public class MainActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private Menu menu;
    BluetoothSPP bt;

    void initBluetooth() {
        bt = new BluetoothSPP(this, AppState.DELIMITER);

        if(!bt.isBluetoothAvailable()) {
            Toast.makeText(getApplicationContext()
                    , "Bluetooth is not available"
                    , Toast.LENGTH_SHORT).show();
            finish();
        }

        bt.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
            public void onDataReceived(byte[] data, String message) {
                String parsedMsg;
                try {
                    parsedMsg = Utils.btDataChunkParser(message);
                }
                catch (Exception e) {
                    parsedMsg = e.toString();
                }
//                Toast.makeText(getApplicationContext(), parsedMsg, Toast.LENGTH_SHORT).show();
            }
        });

        bt.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
            public void onDeviceDisconnected() {
                Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                AppState.getInstance().speakMessage("Disconnected");
                AppState.getInstance().onDisconnected();
                toggleConnectionMenu(false);
            }

            public void onDeviceConnectionFailed() {
                Toast.makeText(getApplicationContext(), "Connection failed", Toast.LENGTH_SHORT).show();
                AppState.getInstance().speakMessage("Connection failed");
                toggleConnectionMenu(false);
            }

            public void onDeviceConnected(String name, String address) {
                String txt = "Connected to " + name;
                Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show();
                AppState.getInstance().speakMessage("Connected");
                toggleConnectionMenu(true);
                AppState.getInstance().onConnected();
            }
        });
    }

    void toggleConnectionMenu(boolean isConnected) {
        menu.findItem(R.id.menuConnect).setVisible(!isConnected);
        menu.findItem(R.id.menuDisconnect).setVisible(isConnected);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        initBluetooth();
        AppState.getInstance().textSpeaker = new TextSpeaker(getApplicationContext());
        AppState.getInstance().preferences = getPreferences(MODE_PRIVATE);
        AppPreferences.applyAll();

        //this will cleanup csv reports after 2 weeks (14 days)
        cleanUpCSVReports();
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

        if(id == R.id.menuConnect) {
            bt.setDeviceTarget(BluetoothState.DEVICE_OTHER);
            Intent intent = new Intent(getApplicationContext(), DeviceList.class);
            startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
        } else if(id == R.id.menuDisconnect) {
            if(bt.getServiceState() == BluetoothState.STATE_CONNECTED)
                bt.disconnect();
        }
        return super.onOptionsItemSelected(item);
    }

    public void onStart() {
        super.onStart();
        if (!bt.isBluetoothEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BluetoothState.REQUEST_ENABLE_BT);
        } else {
            if(!bt.isServiceAvailable()) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_OTHER);
                setup();
            }
        }
    }

    /**
     * This function will cleanUp CSV Reports if it has been 2 weeks since file is last updated.
     */
    public void cleanUpCSVReports(){
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
                if(numDays > 14){
                    try{
                        currFile.delete();
                    } catch (Exception e){
                        continue;
                    }


                }
            }
        }

    }

    public void setup() {
        AppState.getInstance().bt = bt;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if(resultCode == Activity.RESULT_OK)
                bt.connect(data);
        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_OTHER);
                setup();
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

}
