package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 10/21/2017.
 */

public interface ConnectionListener {
    public void onConnected(String name);
    public void onDisconnected();
    public void onConnectionFailed(String errorMsg);
    public void onDataReceived(String message);
}
