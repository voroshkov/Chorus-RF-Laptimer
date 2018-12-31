void runLapDetectionAlgorithm() {
    // check rssi threshold to identify when drone finishes the lap
    if (rssiThreshold > 0) { // threshold = 0 means that we don't check rssi values
        if(rssi > rssiThreshold) { // rssi above the threshold - drone is near
            if (allowLapGeneration) {  // we haven't fired event for this drone proximity case yet
                allowLapGeneration = 0;

                now = millis();
                uint32_t diff = now - lastMilliseconds; //time diff with the last lap (or with the race start)
                if (timeAdjustment != 0 && timeAdjustment != INFINITE_TIME_ADJUSTMENT) {
                    diff = diff + (int32_t)diff/timeAdjustment;
                }
                if (raceMode) { // if we're within the race, then log lap time
                    if (diff > minLapTime*1000 || (!shouldWaitForFirstLap && newLapIndex == 0)) { // if minLapTime haven't passed since last lap, then it's probably false alarm
                        // digitalLow(ledPin);
                        if (newLapIndex < MAX_LAPS-1) { // log time only if there are slots available
                            if (raceMode == 1) {
                                // for the raceMode 1 count time spent for each lap
                                lapTimes[newLapIndex] = diff;
                            } else {
                                // for the raceMode 2 count times relative to the race start (ever-growing with each new lap within the race)
                                uint32_t diffStart = now - raceStartTime;
                                if (timeAdjustment != 0 && timeAdjustment != INFINITE_TIME_ADJUSTMENT) {
                                    diffStart = diffStart + (int32_t)diffStart/timeAdjustment;
                                }
                                lapTimes[newLapIndex] = diffStart;
                            }
                            newLapIndex++;
                            lastLapsNotSent++;
                            addToSendQueue(SEND_LAST_LAPTIMES);
                        }
                        lastMilliseconds = now;
                        playLapTones(); // during the race play tone sequence even if no more laps can be logged
                    }
                }
                else {
                    playLapTones(); // if not within the race, then play once per case
                }
            }
        }
        else  {
            allowLapGeneration = 1; // we're below the threshold, be ready to catch another case
            // digitalHigh(ledPin);
        }
    }
}


void runExperimentalLapDetectionAlgorithm() {
    // check rssi threshold to identify when drone finishes the lap
    if (rssiThreshold > 0) { // threshold = 0 means that we don't check rssi values and not tracking laps
        if (raceMode) {
            rssi2 = getDeepFilteredRSSI();
            rssi3 = getSmoothlyFilteredRSSI();
            now = millis();

            if (newLapIndex == 0 && !shouldWaitForFirstLap) {
                if (checkIsLeadInLapDetected()) {
                    registerNewLap();
                }
            } else {
                if (checkIsMaxRssiDetectionTimeoutNotExpired()) {
                    findMaxRssi();
                    findMaxDeepRssi();
                }

                findMinDeepRssi();
                checkIfDroneLeftDeviceArea();

                if (didLeaveDeviceAreaThisLap) {
                    prepareLapDetectionValues();
                }

                if (checkIsLapDetectionTimeoutExpired()) {
                    if (didLeaveDeviceAreaThisLap) {
                        if (checkIsLapDetected()) {
                            registerNewLap();
                        }
                    }
                }
            }
        } else { // standby mode - beep on all events
            if(rssi > rssiThreshold) { // rssi above the threshold - drone is near
                if (allowLapGeneration) {  // we haven't fired event for this drone proximity case yet
                    playLapTones(); // if not within the race, then play once per case
                    allowLapGeneration = 0;
                }
            } else { // rssi below the threshold - drone is either not yet near or already left the high-rssi laptimer zone - allow lap generation
                allowLapGeneration = 1;
            }
        }
    }
}
