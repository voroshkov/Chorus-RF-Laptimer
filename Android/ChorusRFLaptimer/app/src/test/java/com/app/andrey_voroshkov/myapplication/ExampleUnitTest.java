package com.app.andrey_voroshkov.myapplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Method;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.andrey_voroshkov.chorus_laptimer.AppState;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class ExampleUnitTest {

    BluetoothSPP bt = Mockito.mock(BluetoothSPP.class);

    AppState getFreshAppState() {
        Mockito.doNothing().when(bt).send("");
        AppState.Reset_Instance_TEST_ONLY();
        AppState as = AppState.getInstance();
        as.bt = bt;
        return as;
    }

    @Test
    public void getPilotPositionByTotalTime_dont_fail_when_devices_not_initialized() throws Exception {
        AppState as = getFreshAppState();
        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(-1, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_returns_first_places_for_empty_race() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);
        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(1, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_returns_equal_places_for_equal_results() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);
        as.addLapResult(0, 0, 1);
        as.addLapResult(1, 0, 1);
        as.addLapResult(2, 0, 1);
        as.addLapResult(3, 0, 1);
        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(1, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_returns_equal_places_for_several_equal_results() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);
        as.shouldSkipFirstLap = false;
        as.addLapResult(0, 0, 2);
        as.addLapResult(1, 0, 2);
        as.addLapResult(2, 0, 1);
        as.addLapResult(3, 0, 1);
        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(3, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(3, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(1, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_returns_different_places_for_different_results() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);

        as.shouldSkipFirstLap = false;

        as.addLapResult(0, 0, 10);
        as.addLapResult(0, 1, 1);

        as.addLapResult(1, 0, 5);
        as.addLapResult(1, 1, 1);

        as.addLapResult(2, 0, 3);
        as.addLapResult(2, 1, 1);

        as.addLapResult(3, 0, 1);
        as.addLapResult(3, 1, 1);

        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(4, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(3, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(2, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(1, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_considers_number_of_passed_laps() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);

        as.shouldSkipFirstLap = false;

        as.addLapResult(0, 0, 10);
        as.addLapResult(0, 1, 1);
        as.addLapResult(0, 2, 100);
        as.addLapResult(0, 3, 1000);


        as.addLapResult(1, 0, 5);
        as.addLapResult(1, 1, 1000);
        as.addLapResult(1, 2, 10000);

        as.addLapResult(2, 0, 1);
        as.addLapResult(2, 1, 1);

        as.addLapResult(3, 0, 1);
        as.addLapResult(3, 1, 1);

        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(2, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(3, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(3, pos);
    }

    @Test
    public void getPilotPositionByTotalTime_considers_only_race_laps() throws Exception {
        AppState as = getFreshAppState();
        as.setNumberOfDevices(4);

        as.shouldSkipFirstLap = true;
        as.raceState.lapsToGo = 3;

        as.addLapResult(0, 0, 1);//skipped
        as.addLapResult(0, 1, 1);
        as.addLapResult(0, 2, 1);
        as.addLapResult(0, 3, 1);
        as.addLapResult(0, 4, 1);//skipped
        as.addLapResult(0, 5, 10000);//skipped


        as.addLapResult(1, 0, 1);//skipped
        as.addLapResult(1, 1, 1);
        as.addLapResult(1, 2, 1);
        as.addLapResult(1, 3, 1);
        as.addLapResult(1, 4, 1);//skipped

        as.addLapResult(2, 0, 10000); //skipped
        as.addLapResult(2, 1, 1);
        as.addLapResult(2, 2, 1);
        as.addLapResult(2, 3, 1);

        as.addLapResult(3, 0, 10000); //skipped
        as.addLapResult(3, 1, 1);
        as.addLapResult(3, 2, 1);
        as.addLapResult(3, 3, 1);

        int pos = as.getPilotPositionByTotalTime(0);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(1);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(2);
        assertEquals(1, pos);
        pos = as.getPilotPositionByTotalTime(3);
        assertEquals(1, pos);
    }
}