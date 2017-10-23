package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.content.Intent;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;

/**
 * Created by Andrey_Voroshkov on 10/21/2017.
 */

public class BTService implements Connection {
    private BluetoothSPP mBT = null;

    BTService(Context context, byte delimiter) {
        mBT = new BluetoothSPP(context, delimiter);
    }

    boolean isBluetoothAvailable() {
        return mBT.isBluetoothAvailable();
    }

    void setDataReceiveListener(BluetoothSPP.OnDataReceivedListener listener) {
        mBT.setOnDataReceivedListener(listener);
    }

    void setConnectionListener(BluetoothSPP.BluetoothConnectionListener listener) {
        mBT.setBluetoothConnectionListener(listener);
    }

    void stopService() {
        mBT.stopService();
    }

    void setDeviceTarget(boolean isAndroid) {
        mBT.setDeviceTarget(isAndroid);
    }


    int getServiceState() {
        return mBT.getServiceState();
    }

    void runService() {
        mBT.setupService();
        mBT.startService(BluetoothState.DEVICE_OTHER);
    }

    boolean isBluetoothEnabled() {
        return mBT.isBluetoothEnabled();
    }

    boolean isServiceAvailable() {
        return mBT.isServiceAvailable();
    }

    void connect(Intent data) {
        mBT.connect(data);
    }

    @Override
    public void send(String data) {
        mBT.send(data);
    }

    @Override
    public void setConnectionListener(final ConnectionListener listener) {
        mBT.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
            @Override
            public void onDeviceConnected(String name, String address) {
                listener.onConnected(name);
            }

            @Override
            public void onDeviceDisconnected() {
                listener.onDisconnected();
            }

            @Override
            public void onDeviceConnectionFailed() {
                listener.onConnectionFailed("");
            }
        });

        mBT.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
            @Override
            public void onDataReceived(byte[] data, String message) {
                listener.onDataReceived(message);
            }
        });
    }

    @Override
    public void connect() {
        mBT.connect("");
    }

    @Override
    public void disconnect() {
        mBT.disconnect();
    }
}
