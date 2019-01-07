// forward declarations of 2 functions from main .ino file because this file is included earlier
uint8_t addToSendQueue(uint8_t item);
void resetFieldsAfterLapDetection();


// depends on a lot of global variables. to be used inside a rssi check logic in race mode
void registerNewLap() {
    // register a lap
    if (newLapIndex < MAX_LAPS-1) { // log time only if there are slots available
        // for the raceMode 1 count time spent for each lap
        // for the raceMode 2 count times relative to the race start (ever-growing with each new lap within the race)
        uint32_t lastEventTime = (raceMode == 1) ? lastMilliseconds : raceStartTime;

        // adjust time using timeAdjustment value
        uint32_t diff = now - lastEventTime;
        if (timeAdjustment != 0 && timeAdjustment != INFINITE_TIME_ADJUSTMENT) {
            diff = diff + (int32_t)diff / timeAdjustment;
        }

        lapTimes[newLapIndex] = diff;
        newLapIndex++;
        lastLapsNotSent++;
        addToSendQueue(SEND_LAST_LAPTIMES);
    }
    lastMilliseconds = now;
    playLapTones(); // during the race play tone sequence even if no more laps can be logged
    resetFieldsAfterLapDetection();
}

void findMaxRssi () {
    if (rssi > maxRssi) {
        maxRssi = rssi;
        maxRssiDetectionTime = now;
    }
}

void findMaxDeepRssi () {
    if (rssi2 > maxDeepRssi) {
        maxDeepRssi = rssi2;
        maxDeepRssiDetectionTime = now;
    }
}

void findMinDeepRssi () {
    if (rssi2 < minDeepRssi) {
        minDeepRssi = rssi2;
        minDeepRssiDetectionTime = now;
    }
}

bool checkIsLeadInLapDetected() {
    if (rssi > rssiThreshold) {
        return true;
    }
    return false;
    // TODO: maybe add logic to reuse the threshold already detected in previous races?
}

bool checkIsMaxRssiDetectionTimeoutNotExpired() {
    uint32_t diff = now - lastMilliseconds;
    return diff < DEFAULT_MAX_RSSI_SEARCH_DELAY && diff < minLapTime * 1000; // max detection time must not exceed minLapTime
}

bool checkIsLapDetectionTimeoutExpired() {
    if (isLapDetectionTimeoutExpired) return true;

    uint32_t diff = now - lastMilliseconds;
    isLapDetectionTimeoutExpired = diff > minLapTime * 1000;
    return isLapDetectionTimeoutExpired;
}

void prepareLapDetectionValues() {
    upperSecondLevelRssiThreshold = maxDeepRssi - SECOND_LEVEL_RSSI_DETECTION_ADJUSTMENT;
}

void checkIfDroneLeftDeviceArea() {
    if (didLeaveDeviceAreaThisLap) return;

    if (rssi3 < lowerSecondLevelThreshold) {
        didLeaveDeviceAreaThisLap = true;
        prepareLapDetectionValues();
    }
}

bool checkIsLapDetected() {
    // handle first lap in a special way
    if (newLapIndex == 0) {
        // TODO: add logic to reuse the threshold already detected in previous races
        return rssi > rssiThreshold;
    }

    // for other laps detect immediately if second threshold is crossed
    if (rssi > upperSecondLevelRssiThreshold) {
        return true;
    }

    // otherwise mark the proximity thresholds crossing
    // --- the commented part in the condition below would make sure that the proximity is not tracked before threshold. is it needed?
    if (rssi > upperSecondLevelRssiThreshold - PROXIMITY_STEPS /*&& rssi > rssiThreshold*/) {
        isApproaching = true;
        digitalHigh(ledPin); // debug only

        uint16_t diffWithThreshold = upperSecondLevelRssiThreshold - rssi;
        if (diffWithThreshold < currentProximityIndex) {
            currentProximityIndex = diffWithThreshold;
            currentProximityIndexTime = now;
        }
    }

    if (isApproaching) {
        uint32_t timeDiff = now - currentProximityIndexTime;
        if (timeDiff > proximityTimesArray[currentProximityIndex]) { // time's up
            // substitute now with saved time of max value detection. DON'T USE the "now" AFTER THIS,EXCEPT FOR RECORDING A NEW LAP
            now = currentProximityIndexTime;
            return true; //record a lap
        }
    }

    // // first mark the initial threshold crossing
    // if (rssi > upperSecondLevelRssiThreshold - RELIABLE_RSSI_DETECTION_SUBTRACT) {
    //     if (!isFirstThresholdCrossed) {
    //         isFirstThresholdCrossed = true;
    //         timeWhenFirstThresholdCrossed = now;

    //         maxDeepRssiAfterFirstThreshold = rssi2;
    //         timeWhenMaxAfterFirstThresholdWasDetected = now;
    //     }
    // }
    // // find max rssi after first threshold has been crossed
    // if (isFirstThresholdCrossed) {
    //     if (rssi > maxDeepRssiAfterFirstThreshold) {
    //         maxDeepRssiAfterFirstThreshold = rssi2;
    //         timeWhenMaxAfterFirstThresholdWasDetected = now;
    //     }
    // }


    // // otherwise wait for certain time after first threshold and use found max value and time
    // else {
    //     uint32_t diff = now - timeWhenFirstThresholdCrossed;
    //     if (isFirstThresholdCrossed && diff > ALLOWED_LAP_DETECTION_TIMEOUT) {
    //         DEBUG_CODE(
    //             Serial.print("---detectionTimeout---");
    //         );
    //         // substitute now with saved time of max value detection. DON'T USE the "now" AFTER THIS,EXCEPT FOR RECORDING A NEW LAP
    //         now = timeWhenMaxAfterFirstThresholdWasDetected;
    //         maxDeepRssi = maxDeepRssiAfterFirstThreshold; // we couldn't detect a lap within allowed detection interval, so use the detected max as global maximum
    //         return true;
    //     }
    // }


    // none of the above worked, i.e. no lap is detected
    return false;
}

void resetFieldsAfterLapDetection() {
    digitalLow(ledPin);
    isApproaching = false;
    isLapDetectionTimeoutExpired = false;
    currentProximityIndex = 0xFF;
    isFirstThresholdCrossed = false;
    didLeaveDeviceAreaThisLap = false;
    timeWhenFirstThresholdCrossed = 0;
    lowerSecondLevelThreshold = (maxDeepRssi + minDeepRssi) / 2; // mid value between maxDeepRssi and minDeepRssi
    maxDeepRssi = maxDeepRssi > rssiThreshold ? maxDeepRssi : rssiThreshold; // init with the max known value
    minDeepRssi = minDeepRssi < rssiThreshold ? minDeepRssi : rssiThreshold; // init with the min known value
    maxDeepRssi -= EDGE_RSSI_ADJUSTMENT; // this is to make sure that maxDeepRssi doesn't accumulate the ever-max value during the race
    minDeepRssi += EDGE_RSSI_ADJUSTMENT; // this is to make sure that minDeepRssi doesn't accumulate the ever-min value during the race
}

void resetFieldsBeforeRaceStart() {
    isApproaching = false;
    isLapDetectionTimeoutExpired = false;
    currentProximityIndex = 0xFF;
    isFirstThresholdCrossed = false;
    maxDeepRssi = rssiThreshold - EDGE_RSSI_ADJUSTMENT; // must be below rssiThreshold
    minDeepRssi = rssiThreshold;
    lowerSecondLevelThreshold = rssiThreshold;
}
