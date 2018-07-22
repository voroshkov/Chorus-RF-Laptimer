package app.andrey_voroshkov.chorus_laptimer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Andrey_Voroshkov on 10/15/2017.
 */

public class UDPService implements Connection{
    final static int MAX_SEND_PACKET_SIZE = 20;
    final static int MAX_UDP_PACKET_SIZE = 65507;

    final static String KEY_MSG_TYPE = "msg";
    final static String KEY_MSG_DATA = "data";

    final static int MSG_ON_CONNECT = 7771;
    final static int MSG_ON_DISCONNECT = 7772;
    final static int MSG_ON_RECEIVE = 7773;
    final static int MSG_ON_CONNECTION_FAIL = 7774;

    final static String DEFAULT_IP = "192.168.4.1";
    final static int DEFAULT_PORT = 9000;

    String mAddress = DEFAULT_IP;
    int mPort = DEFAULT_PORT;

    ByteBuffer mSendBuf = ByteBuffer.allocateDirect(MAX_SEND_PACKET_SIZE);
    ByteBuffer mReceiveBuf = ByteBuffer.allocateDirect(MAX_UDP_PACKET_SIZE);

    volatile DatagramChannel mChannel = null;
    ConnectionListener mConnectionListener = null;
    SenderThread mSenderThread = null;
    ListenerThread mListenerThread = null;
    ConnectorThread mConnectorThread = null; // needed because Network cannot be initialized on main thread (exception occurs)
    Handler mActivityHandler = null;


    UDPService() {
        mActivityHandler = new HandlerExtension();
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

    public void connect(String addr, int port) {
        mAddress = addr;
        mPort = port;
        if (addr.isEmpty()) {
            mAddress = DEFAULT_IP;
        }
        if (mPort <= 0 || mPort > 0xFFFF) {
            mPort = DEFAULT_PORT;
        }
        connect();
    }

    public void connect() {
        if (mChannel != null) return;

        CountDownLatch senderInitializedSignal = new CountDownLatch(mSenderThread == null ? 1 : 0);
        CountDownLatch connectionThreadDoneSignal = new CountDownLatch(1);

        mConnectorThread = new ConnectorThread(connectionThreadDoneSignal);
        mConnectorThread.start();
        try {
            connectionThreadDoneSignal.await(); // this will wait until UDP channel opens or fails to open
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mChannel != null) {
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

            mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECT, mAddress));
        }
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

        if (mChannel != null) {
            try {
                mChannel.disconnect();
            }
            catch(Exception e) {
                // TODO: handle exception here ?
            }
            finally {
                mChannel = null;
            }
        }

        mActivityHandler.sendMessage(composeMessage(MSG_ON_DISCONNECT, "disconnect"));
    }

    public void send(String data) {
        if (mChannel == null) return;
        if (mSenderThread == null) return;

        mSenderThread.send(data);
    }


    // this thread is needed because Network cannot be initialized on main thread (exception occurs)
    // it just inits the UDP channel and quits
    private class ConnectorThread extends Thread {
        CountDownLatch mInitializedSignal;

        ConnectorThread(CountDownLatch initializedSignal) {
            super();
            mInitializedSignal = initializedSignal;
        }

        public void run() {
            if (mChannel != null) return;
            try {
                mChannel = DatagramChannel.open();
                mChannel.configureBlocking(false);
                mChannel.connect(new InetSocketAddress(mAddress, mPort));
            } catch (Exception e) {
                mChannel = null;
                mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, e.toString()));
            }
            mInitializedSignal.countDown();
        }
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
            if (mChannel == null) return;
            // TODO: check that there are no situations when we try sending to closed port
            mSendHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mSendBuf.put(data.getBytes());
                        mSendBuf.flip();
                        mChannel.write(mSendBuf);
                        mSendBuf.clear();
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
                if (mChannel == null) continue;
                try {
                    mChannel.read(mReceiveBuf);
                    mReceiveBuf.flip();
                    Charset charset = Charset.forName("ASCII");
                    CharBuffer cbuf = charset.decode(mReceiveBuf);
                    String result = cbuf.toString();
                    parseAndCallback(result);
                    mReceiveBuf.clear();
                }
                catch(ClosedByInterruptException e) {
                    // if thread was interrupted, we should not disconnect, because it's already being disconnected
                }
                catch (Exception e) {
                    disconnect();
                    //TODO: implement some handling here!
                    break;
                }
            }
        }
    }
}