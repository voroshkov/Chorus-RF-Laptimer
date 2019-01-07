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
    // send each 100 ms and only if value was changed since last send
    if (maxRssi != dbgSentMaxRssi && (now - dbgMaxRssiLastSentTime) > 100) {
        addToSendQueue(SEND_DBG_MAX_RSSI);
        dbgMaxRssiLastSentTime = now;
        dbgSentMaxRssi = maxRssi;
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
    if (isLapDetectionTimeoutExpired) {
        addToSendQueue(SEND_DBG_MINLAP_EXPIRED); // send lap detection expired debug info
    }
    return isLapDetectionTimeoutExpired;
}

void prepareLapDetectionValues() {
    upperSecondLevelRssiThreshold = maxDeepRssi - SECOND_LEVEL_RSSI_DETECTION_ADJUSTMENT;
    addToSendQueue(SEND_DBG_DYNAMIC_THRESHOLD);
}

void checkIfDroneLeftDeviceArea() {
    if (didLeaveDeviceAreaThisLap) return;

    if (rssi3 < lowerSecondLevelThreshold) {
        didLeaveDeviceAreaThisLap = true;
        addToSendQueue(SEND_DBG_LEFT_DEVICE_AREA); // send LeaveDeviceArea debug notification
        prepareLapDetectionValues();
    }
}

void sendDebugProximityIndex() {
    static uint8_t lastSentIdx = 200; //something different :)
    uint8_t indexToSend = currentProximityIndex & B11111110; // only keep even numbers to decrease load on android app
    if (lastSentIdx == indexToSend) {
        // dont send same value twice ( if 2 sends are subsequent then we may change currentProximityIndex before sending it, thus send same value twice or omit some values)
        onItemSent();
        return;
    }
    if (sendByteToSerial(RESPONSE_DBG_PROXIMITY_IDX, indexToSend)) {
        lastSentIdx = indexToSend;
        onItemSent();
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
        currentProximityIndex = 100; // lap detected by threshold
        return true;
    }

    // otherwise mark the proximity thresholds crossing
    // --- the commented part in the condition below would make sure that the proximity is not tracked before threshold. is it needed?
    if (rssi > (upperSecondLevelRssiThreshold - PROXIMITY_STEPS) /*&& rssi > rssiThreshold*/) {
        isApproaching = true;
        digitalHigh(ledPin); // debug only

        uint16_t diffWithThreshold = upperSecondLevelRssiThreshold - rssi;
        if (diffWithThreshold < currentProximityIndex) {
            currentProximityIndex = diffWithThreshold;
            currentProximityIndexTime = now;
            addToSendQueue(SEND_DBG_PROXIMITY_IDX); // send debug proximity index
            playNote(musicNotes[PROXIMITY_STEPS - currentProximityIndex]);
        }
    }

    if (isApproaching) {
        uint32_t timeDiff = now - currentProximityIndexTime;
        if (timeDiff > proximityTimesArray[currentProximityIndex]) { // time's up
            // substitute now with saved time of max value detection. DON'T USE the "now" AFTER THIS,EXCEPT FOR RECORDING A NEW LAP
            now = currentProximityIndexTime;
            currentProximityIndex = 255; // lap detected by timeout
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

void sendDeviceDebugData() {
    addToSendQueue(SEND_DBG_PROXIMITY_IDX);
    addToSendQueue(SEND_DBG_MINLAP_EXPIRED);
    addToSendQueue(SEND_DBG_LEFT_DEVICE_AREA);
    addToSendQueue(SEND_DBG_DYNAMIC_THRESHOLD);
    addToSendQueue(SEND_DBG_MAX_RSSI);
}

void resetFieldsAfterLapDetection() {
    digitalLow(ledPin);
    isApproaching = false;
    isLapDetectionTimeoutExpired = false;
    // currentProximityIndex = 0xFF;
    isFirstThresholdCrossed = false;
    didLeaveDeviceAreaThisLap = false;
    timeWhenFirstThresholdCrossed = 0;
    maxRssi = 0; // debug only
    dbgSentMaxRssi = 0; //debug only
    lowerSecondLevelThreshold = (maxDeepRssi + minDeepRssi) / 2; // mid value between maxDeepRssi and minDeepRssi
    maxDeepRssi = maxDeepRssi > rssiThreshold ? maxDeepRssi : rssiThreshold; // init with the max known value
    minDeepRssi = minDeepRssi < rssiThreshold ? minDeepRssi : rssiThreshold; // init with the min known value
    maxDeepRssi -= EDGE_RSSI_ADJUSTMENT; // this is to make sure that maxDeepRssi doesn't accumulate the ever-max value during the race
    minDeepRssi += EDGE_RSSI_ADJUSTMENT; // this is to make sure that minDeepRssi doesn't accumulate the ever-min value during the race
    sendDeviceDebugData();
}


void resetFieldsBeforeRaceStart() {
    maxRssi = 0; // debug only
    dbgSentMaxRssi = 0;// debug only
    isApproaching = false;
    isLapDetectionTimeoutExpired = false;
    currentProximityIndex = 0xFF;
    isFirstThresholdCrossed = false;
    maxDeepRssi = rssiThreshold - EDGE_RSSI_ADJUSTMENT; // must be below rssiThreshold
    minDeepRssi = rssiThreshold;
    lowerSecondLevelThreshold = rssiThreshold;
    dbgMaxRssiLastSentTime = millis();
    sendDeviceDebugData();
}
