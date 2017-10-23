package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 10/21/2017.
 */

public interface Connection {
    public void connect();
    public void disconnect();
    public void send(String data);
    public void setConnectionListener(ConnectionListener listener);
}
