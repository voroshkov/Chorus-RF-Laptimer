package app.andrey_voroshkov.chorus_laptimer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;

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

    DatagramChannel mChannel = null;
    ConnectionListener mConnectionListener = null;
    String mAddress = DEFAULT_IP;
    int mPort = DEFAULT_PORT;
    boolean mIsConnected = false;
    ByteBuffer mSendBuf = ByteBuffer.allocateDirect(MAX_SEND_PACKET_SIZE);
    ByteBuffer mReceiveBuf = ByteBuffer.allocateDirect(MAX_UDP_PACKET_SIZE);
    ListenerThread mListenerThread = null;
    ConnectionThread mConnectionThread = null;
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
                    mConnectionListener.onConnected("");
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
        if (mConnectionThread != null) return;
        mConnectionThread = new ConnectionThread();
        mConnectionThread.start();
    }

    public void disconnect() {
        if (!mIsConnected) return;
        try {
            mListenerThread = null;
            mConnectionThread = null;
            mChannel.disconnect();
        }
        catch(Exception e) {
            //TODO: handle exception here ?
        }
        mIsConnected = false;
        mActivityHandler.sendMessage(composeMessage(MSG_ON_DISCONNECT, "disconnect"));
    }

    public void send(String data) {
        ConnectionThread thread;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (!mIsConnected || mConnectionThread == null) return;
            thread = mConnectionThread;
        }
        // Perform the write unsynchronized
        thread.send(data);
    }

    private class ConnectionThread extends Thread {

        private Handler mSendHandler;

        public void run () {
            if (mIsConnected) return;
            try {
                mChannel = DatagramChannel.open();
                mChannel.configureBlocking(false);
                mChannel.connect(new InetSocketAddress(mAddress, mPort));

                if (mListenerThread == null) {
                    mListenerThread = new ListenerThread();
                    mListenerThread.start();
                    mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECT, ""));
                    mIsConnected = true;
                }

                Looper.prepare();
                mSendHandler = new Handler();
                Looper.loop();

            } catch (Exception e) {
                mActivityHandler.sendMessage(composeMessage(MSG_ON_CONNECTION_FAIL, e.toString()));
            }
        }

        public void send(final String data) {
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
            while (true) {
                try {
                    mChannel.read(mReceiveBuf);
                    mReceiveBuf.flip();
                    Charset charset = Charset.forName("ASCII");
                    CharBuffer cbuf = charset.decode(mReceiveBuf);
                    String result = cbuf.toString();
                    parseAndCallback(result);
                    mReceiveBuf.clear();
                } catch (Exception e) {
                    disconnect();
                    //TODO: implement some handling here!
                    break;
                }
            }
        }
    }
}