package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 1/28/2017.
 */

public class RaceState {
    public int minLapTime;
    public boolean isStarted;
    public int lapsToGo;

    RaceState(boolean isStarted, int minLapTime, int lapsToGo) {
        this.minLapTime = minLapTime;
        this.isStarted = isStarted;
        this.lapsToGo = lapsToGo;
    }
}
