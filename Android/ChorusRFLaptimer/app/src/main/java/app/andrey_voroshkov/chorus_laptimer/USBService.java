package app.andrey_voroshkov.chorus_laptimer;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static android.content.ContentValues.TAG;

/**
 * Created by Andrey_Voroshkov on 10/15/2017.
 */

public class USBService implements Connection{
    final static String KEY_MSG_TYPE = "msg";
    final static String KEY_MSG_DATA = "data";

    final static int MSG_ON_CONNECT = 7771;
    final static int MSG_ON_DISCONNECT = 7772;
    final static int MSG_ON_RECEIVE = 7773;
    final static int MSG_ON_CONNECTION_FAIL = 7774;

    final static int SEND_TIMEOUT = 100;
    final static int READ_TIMEOUT = 100;

    ConnectionListener mConnectionListener = null;
    Handler mActivityHandler = null;
    UsbManager mUsbManager = null;
    ListenerThread mListenerThread = null;
    SenderThread mSenderThread = null;
    volatile UsbSerialPort mPort = null;

    USBService(UsbManager usbManager) {
        mActivityHandler = new HandlerExtension();
        mUsbManager = usbManager;
    }

    private class HandlerExtension extends Handler {
        @Override
        public void handleMessage(Message message){
            if (mConnectionListener == null) return;

            Bundle msgBundle = message.getData();
            int msgType = msgBundle.getInt(KEY_MSG_TYPE);
            String data = msgBundle.getString(KEY_MSG_DATA);
            switch(msgType) {
                case MSG_ON_CONNECT:
                    mConnectionListener.onConnected(data);
                    break;
                case MSG_ON_CONNECTION_FAIL:
                    mConnectionListener.onConnectionFailed(data);
                    break;
                case MSG_ON_DISCONNECT:
                    mConnectionListener.onDisconnected();
                    break;
                case MSG_ON_RECEIVE:
                    mConnectionListener.onDataReceived(data);
                    break;
            }
        }
    }

    private Message composeMessage(int type, String data) {
        Bundle msgBundle = new Bundle();
        msgBundle.putInt(KEY_MSG_TYPE, type);
        msgBundle.putString(KEY_MSG_DATA, data);
        Message msg = new Message();
        msg.setData(msgBundle);
        return msg;
    }

    public void setConnectionListener(ConnectionListener listener) {
        mConnectionListener = listener;
    }

    @Override
    public void connect() {
        if (mPort != null) return;

        if (mUsbManager == null) {
            mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, "UsbManager not found"));
            return;
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (availableDrivers.isEmpty()) {
            mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, "No available USB Device Drivers found"));
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = mUsbManager.openDevice(driver.getDevice());
        if (connection == null) {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, "Cannot open USB data port"));
            return;
        }

        // Read some data! Most have just one port (port 0).
        mPort = driver.getPorts().get(0);
        try {
            mPort.open(connection);
            mPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            try {
                mPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            mPort = null;
            mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, e.toString()));
            return;
        }

        // create a latch to make sure that sending thread is started before we send anything to USB
        CountDownLatch senderInitializedSignal = new CountDownLatch(mSenderThread == null ? 1 : 0);

        if (mListenerThread == null) {
            mListenerThread = new ListenerThread();
            mListenerThread.start();
        }

        if (mSenderThread == null) {
            mSenderThread = new SenderThread(senderInitializedSignal);
            mSenderThread.start();
        }

        try {
            senderInitializedSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String deviceName;
        try {
            deviceName = mPort.getDriver().getClass().getSimpleName();
        }
        catch(Exception e) {
            deviceName = "";
        }
        mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECT, deviceName));
    }

    @Override
    public void disconnect() {
        // don't stop the sender thread as it uses a Looper and cannot be easily stopped (to the best of my current knowledge)
        // but stop the listener thread
        if (mListenerThread != null) {
            mListenerThread.interrupt();
            try {
                mListenerThread.join();
                mListenerThread = null;
            }
            catch(InterruptedException e) {
                // do nothing ?
            }
        }

        if (mPort != null) {
            try {
                mPort.close();
            }
            catch(Exception e) {
                // TODO: handle exception here ?
            }
            finally {
                mPort = null;
            }
        }

        mActivityHandler.sendMessage(composeMessage(MSG_ON_DISCONNECT, "disconnect"));
    }

    public void send(String data) {
        if (mPort == null) return;
        if (mSenderThread == null) return;

        mSenderThread.send(data);
    }

    private class SenderThread extends Thread {

        CountDownLatch mInitializedSignal;

        SenderThread(CountDownLatch initializedSignal) {
            super();
            mInitializedSignal = initializedSignal;
        }

        private Handler mSendHandler;

        public void run () {
            // prepare handler to process send commands via messages to SenderThread
            Looper.prepare();

            mSendHandler = new Handler();

            mInitializedSignal.countDown();
            Looper.loop();
        }

        public void send(final String data) {
            if (mPort == null) return;
            // TODO: check that there are no situations when we try sending to closed port
            mSendHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mPort.write(data.getBytes(), SEND_TIMEOUT);
                    } catch (Exception e) {
                        disconnect();
                        //TODO: implement some handling here!
                    }
                }
            });
        }
    }

    private class ListenerThread extends Thread {

        byte[] mReceiveArray = new byte[4098];
        String mLastIncompleteChunk = "";

        private void parseAndCallback(String str) {
            if (mConnectionListener == null || str.length() == 0) return;

            char lastChar = str.charAt(str.length()-1);
            boolean isLastChunkIncomplete = lastChar != '\n';

            String[] chunks = TextUtils.split(str, "\n");
            int lastChunkIndex = chunks.length - 1;

            if (!mLastIncompleteChunk.isEmpty()) {
                chunks[0] = mLastIncompleteChunk + chunks[0];
            }

            if (isLastChunkIncomplete) {
                mLastIncompleteChunk = chunks[lastChunkIndex];
                chunks[lastChunkIndex] = "";
            } else {
                mLastIncompleteChunk = "";
            }

            for (String chunk : chunks) {
                if (chunk.isEmpty()) continue;
                mActivityHandler.sendMessage(composeMessage(MSG_ON_RECEIVE, chunk));
            }
        }

        public void run() {
            while (!isInterrupted()) {
                if (mPort == null) continue;
                try {
                    int len = 0;
                    len = mPort.read(mReceiveArray, READ_TIMEOUT);
                    if (len > 0) {
                        Charset charset = Charset.forName("ASCII");
                        String result = new String(mReceiveArray, 0, len, charset);
                        parseAndCallback(result);
                    }
                } catch (Exception e) {
                    disconnect();
                    //TODO: implement some handling here!
                    break;
                }
            }
        }
    }
}