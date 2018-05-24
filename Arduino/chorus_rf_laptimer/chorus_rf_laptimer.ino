/**
 * DIY RF Laptimer by Andrey Voroshkov (bshep)
 * SPI driver based on fs_skyrf_58g-main.c by Simon Chambers
 * fast ADC reading code is by "jmknapp" from Arduino forum
 * fast port I/O code from http://masteringarduino.blogspot.com.by/2013/10/fastest-and-smallest-digitalread-and.html

The MIT License (MIT)

Copyright (c) 2016 by Andrey Voroshkov (bshep)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/*
TODO: Resolve problem:
    when CONTROL_GET_ALL_DATA is being processed, i.e. all data items are sent in sequence,
    and either external or external logic decides to send an individual item,
    then the latter just breaks execution of CONTROL_GET_ALL_DATA and data is not sent completely
    This behavior should not occur in case of normal send queue processing.

TODO: there's possible optimization in send queue:
    remove already existing items from queue
*/

#define API_VERSION 4 // version number to be increased with each API change (int16)

// #define DEBUG

#ifdef DEBUG
    #define DEBUG_CODE(x) do { x } while(0)
#else
    #define DEBUG_CODE(x) do { } while(0)
#endif

uint8_t MODULE_ID = 0;
uint8_t MODULE_ID_HEX = '0';

#define SERIAL_DATA_DELIMITER '\n'

#include <avr/pgmspace.h>

#define BAUDRATE 115200

const uint16_t musicNotes[] PROGMEM = { 523, 587, 659, 698, 784, 880, 988, 1046 };

// number of analog rssi reads to average for the current check.
// single analog read with FASTADC defined (see below) takes ~20us on 16MHz arduino
// so e.g. 10 reads will take 200 ms, which gives resolution of 5 RSSI reads per ms,
// this means that we can theoretically have 1ms timing accuracy :)
#define RSSI_READS 5 // 5 should give about 10 000 readings per second

// API in brief (sorted alphabetically):
// Req  Resp Description
// 1    1    first lap counts (opposite to prev API)
// B    B    band
// C    C    channel
// F    F    freq
// H    H    threshold setup mode
//      L    lap (response only)
// I    I    rssi monitor interval (0 = off)
// J    J    time adjustment constant
// M    M    min lap time
// R    R    race mode
// S    S    device sounds state
// T    T    threshold
// get only:
// #    #    api version #
// a    ...  all device state
// r    r    rssi value
// t    t    time in milliseconds
// v    v    voltage
//      x    end of sequence sign (response to "a")
// y    y    is module configured (response to "a")

// input control byte constants
// get/set:
#define CONTROL_WAIT_FIRST_LAP      '1'
#define CONTROL_BAND                'B'
#define CONTROL_CHANNEL             'C'
#define CONTROL_FREQUENCY           'F'
#define CONTROL_THRESHOLD_SETUP     'H'
#define CONTROL_RSSI_MON_INTERVAL   'I'
#define CONTROL_TIME_ADJUSTMENT     'J'
#define CONTROL_RACE_MODE           'R'
#define CONTROL_MIN_LAP_TIME        'M'
#define CONTROL_SOUND               'S'
#define CONTROL_THRESHOLD           'T'
// get only:
#define CONTROL_GET_API_VERSION     '#'
#define CONTROL_GET_ALL_DATA        'a'
#define CONTROL_GET_RSSI            'r'
#define CONTROL_GET_TIME            't'
#define CONTROL_GET_VOLTAGE         'v'
#define CONTROL_GET_IS_CONFIGURED   'y'

// output id byte constants
#define RESPONSE_WAIT_FIRST_LAP      '1'
#define RESPONSE_BAND                'B'
#define RESPONSE_CHANNEL             'C'
#define RESPONSE_FREQUENCY           'F'
#define RESPONSE_THRESHOLD_SETUP     'H'
#define RESPONSE_RSSI_MON_INTERVAL   'I'
#define RESPONSE_TIME_ADJUSTMENT     'J'
#define RESPONSE_LAPTIME             'L'
#define RESPONSE_RACE_MODE           'R'
#define RESPONSE_MIN_LAP_TIME        'M'
#define RESPONSE_SOUND               'S'
#define RESPONSE_THRESHOLD           'T'

#define RESPONSE_API_VERSION         '#'
#define RESPONSE_RSSI                'r'
#define RESPONSE_TIME                't'
#define RESPONSE_VOLTAGE             'v'
#define RESPONSE_END_SEQUENCE        'x'
#define RESPONSE_IS_CONFIGURED       'y'

// send item byte constants
// Must correspond to sequence of numbers used in "send data" switch statement
// Subsequent items starting from 0 participate in "send all data" response
#define SEND_CHANNEL            0
#define SEND_RACE_MODE          1
#define SEND_MIN_LAP_TIME       2
#define SEND_THRESHOLD          3
#define SEND_ALL_LAPTIMES       4
#define SEND_SOUND_STATE        5
#define SEND_BAND               6
#define SEND_LAP0_STATE         7
#define SEND_IS_CONFIGURED      8
#define SEND_FREQUENCY          9
#define SEND_MON_INTERVAL       10
#define SEND_TIME_ADJUSTMENT    11
#define SEND_API_VERSION        12
#define SEND_VOLTAGE            13
#define SEND_THRESHOLD_SETUP_MODE 14
#define SEND_END_SEQUENCE       15
// following items don't participate in "send all items" response
#define SEND_LAST_LAPTIMES          100
#define SEND_TIME                   101
#define SEND_CURRENT_RSSI           102
// special item that sends all subsequent items from 0 (see above)
#define SEND_ALL_DEVICE_STATE       255

//----- RSSI --------------------------------------
#define FILTER_ITERATIONS 0 // software filtering iterations; set 0 - if filtered in hardware; set 5 - if not
uint16_t rssiArr[FILTER_ITERATIONS + 1];
uint16_t rssiThreshold = 190;
uint16_t rssi;
uint16_t slowRssi;

#define RSSI_MAX 1024
#define RSSI_MIN 0
#define MAGIC_THRESHOLD_REDUCE_CONSTANT 2
#define THRESHOLD_ARRAY_SIZE  100
uint16_t rssiThresholdArray[THRESHOLD_ARRAY_SIZE];

#define MIN_RSSI_MONITOR_INTERVAL 1 // in milliseconds
uint16_t rssiMonitorInterval = 0; // zero means the RSSI monitor is OFF
uint32_t lastRssiMonitorReading = 0; // millis when rssi monitor value was last read

#define RSSI_SETUP_INITIALIZE 0
#define RSSI_SETUP_NEXT_STEP 1

//----- Voltage monitoring -------------------------
#define VOLTAGE_READS 3 //get average of VOLTAGE_READS readings

// analog readings less than VOLTAGE_ZERO_THRESHOLD value won't be sent.
// This way entire chorus device will send voltages only from devices that are attached to LiPo
// So if single Solo device has LiPo attached, then broadcast voltage request to
// entire Chorus device will produce a single voltage response.
#define VOLTAGE_ZERO_THRESHOLD 100

//----- Send Queue ---------------------------------
#define SEND_QUEUE_MAXLEN 20
uint8_t sendQueue[SEND_QUEUE_MAXLEN];
uint8_t sendQueueHead = 0;
uint8_t sendQueueTail = 0;
uint8_t isSendQueueFull = 0;

//----- Lap timings--------------------------------
uint32_t lastMilliseconds = 0;
uint32_t raceStartTime = 0;
#define MIN_MIN_LAP_TIME 1 //seconds
#define MAX_MIN_LAP_TIME 120 //seconds
uint8_t minLapTime = 1; //seconds
#define MAX_LAPS 100
uint32_t lapTimes[MAX_LAPS];

//----- Time Adjustment (for accuracy) ------------
#define INFINITE_TIME_ADJUSTMENT 0x7FFFFFFFF // max positive 32 bit signed number
// Usage of signed int time adjustment constant inside this firmware:
// * calibratedMs = readMs + readMs/timeAdjustment
// Usage of signed int time adjustment constant from outside:
// * set to zero means time adjustment procedure was not performed for this node
// * set to INFINITE_TIME_ADJUSTMENT, means time adjustment was performed, but no need to adjust
int32_t timeAdjustment = 0;

//----- other globals------------------------------
uint8_t allowLapGeneration = 0;
uint8_t channelIndex = 0;
uint8_t bandIndex = 0;
uint8_t raceMode = 0; // 0: race mode is off; 1: lap times are counted relative to last lap end; 2: lap times are relative to the race start (sum of all previous lap times);
uint8_t isSoundEnabled = 1;
uint8_t isConfigured = 0; //changes to 1 if any input changes the state of the device. it will mean that externally stored preferences should not be applied
uint8_t newLapIndex = 0;
uint8_t shouldWaitForFirstLap = 0; // 0 means start table is before the laptimer, so first lap is not a full-fledged lap (i.e. don't respect min-lap-time for the very first lap)
uint8_t isSendingData = 0;
uint8_t sendStage = 0;
uint8_t sendLapTimesIndex = 0;
uint8_t sendLastLapIndex = 0;
uint8_t shouldSendSingleItem = 0;
uint8_t lastLapsNotSent = 0;
uint8_t thresholdSetupMode = 0;
uint16_t frequency = 0;
uint32_t millisUponRequest = 0;

//----- read/write bufs ---------------------------
#define READ_BUFFER_SIZE 20
uint8_t readBuf[READ_BUFFER_SIZE];
uint8_t proxyBuf[READ_BUFFER_SIZE];
uint8_t readBufFilledBytes = 0;
uint8_t proxyBufDataSize = 0;

// ----------------------------------------------------------------------------
#include "fastReadWrite.h"
#include "fastADC.h"
#include "pinAssignments.h"
#include "channels.h"
#include "sendSerialHex.h"
#include "rx5808spi.h"
#include "sounds.h"

// ----------------------------------------------------------------------------
void setup() {
    // initialize led pin as output.
    pinMode(ledPin, OUTPUT);
    digitalHigh(ledPin);

    // init buzzer pin
    pinMode(buzzerPin, OUTPUT);

    //init raspberrypi interrupt generator pin
    pinMode(pinRaspiInt, OUTPUT);
    digitalLow(pinRaspiInt);

    // SPI pins for RX control
    setupSPIpins();

    // set the channel as soon as we can
    // faster boot up times :)
    frequency = setModuleChannel(channelIndex, bandIndex);

    Serial.begin(BAUDRATE);

    initFastADC();

    // Setup Done - Turn Status ledPin off.
    digitalLow(ledPin);

    DEBUG_CODE(
        pinMode(serialTimerPin, OUTPUT);
        pinMode(loopTimerPin, OUTPUT);
        pinMode(bufferBusyPin, OUTPUT);
        pinMode(dbgPin, OUTPUT);
    );
}
// ----------------------------------------------------------------------------
void loop() {
    DEBUG_CODE(
        digitalToggle(loopTimerPin);
    );

    // TODO: revise if additional filtering is really needed
    // TODO: if needed, then maybe use the same algorithm, as getSlowChangingRSSI to avoid decrease of values?
    rssi = getFilteredRSSI(); // actually it doesn't filter

    if (!raceMode) { // no need to get slowRssi during race time because it's used only in threshold setting, which is already set by the race time
        slowRssi = getSlowChangingRSSI(); // filter RSSI
    }

    // check rssi threshold to identify when drone finishes the lap
    if (rssiThreshold > 0) { // threshold = 0 means that we don't check rssi values
        if(rssi > rssiThreshold) { // rssi above the threshold - drone is near
            if (allowLapGeneration) {  // we haven't fired event for this drone proximity case yet
                allowLapGeneration = 0;

                uint32_t now = millis();
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

    readSerialDataChunk();

    sendProxyDataChunk();

    // send data chunk through Serial

    if (isSendingData) {
        switch (sendStage) {
            case 0: // SEND_CHANNEL
                if (send4BitsToSerial(RESPONSE_CHANNEL, channelIndex)) {
                    onItemSent();
                }
                break;
            case 1: // SEND_RACE_MODE
                if (send4BitsToSerial(RESPONSE_RACE_MODE, raceMode)) {
                    DEBUG_CODE(
                        digitalLow(serialTimerPin);
                    );
                    onItemSent();
                }
                break;
            case 2: // SEND_MIN_LAP_TIME
                if (sendByteToSerial(RESPONSE_MIN_LAP_TIME, minLapTime)) {
                    onItemSent();
                }
                break;
            case 3: // SEND_THRESHOLD
                if (sendIntToSerial(RESPONSE_THRESHOLD, rssiThreshold)) {
                    onItemSent();
                }
                break;
            case 4: // SEND_ALL_LAPTIMES
                if (sendLapTimesIndex < newLapIndex) {
                    if (sendLaptimeToSerial(RESPONSE_LAPTIME, sendLapTimesIndex, lapTimes[sendLapTimesIndex])) {
                        sendLapTimesIndex++;
                    }
                }
                else {
                    onItemSent();
                }
                break;
            case 5: // SEND_SOUND_STATE
                if (send4BitsToSerial(RESPONSE_SOUND, isSoundEnabled)) {
                    onItemSent();
                }
                break;
            case 6: // SEND_BAND
                if (send4BitsToSerial(RESPONSE_BAND, bandIndex)) {
                    onItemSent();
                }
                break;
            case 7: // SEND_LAP0_STATE
                if (send4BitsToSerial(RESPONSE_WAIT_FIRST_LAP, shouldWaitForFirstLap)) {
                    onItemSent();
                }
                break;
            case 8: // SEND_IS_CONFIGURED
                if (send4BitsToSerial(RESPONSE_IS_CONFIGURED, isConfigured)) {
                    onItemSent();
                }
                break;
            case 9: // SEND_FREQUENCY
                if (sendIntToSerial(RESPONSE_FREQUENCY, frequency)) {
                    onItemSent();
                }
                break;
            case 10: // SEND_MON_INTERVAL
                if (sendIntToSerial(RESPONSE_RSSI_MON_INTERVAL, rssiMonitorInterval)) {
                    onItemSent();
                }
                break;
            case 11: // SEND_TIME_ADJUSTMENT
                if (sendLongToSerial(RESPONSE_TIME_ADJUSTMENT, timeAdjustment)) {
                    onItemSent();
                }
                break;
            case 12: // SEND_API_VERSION
                if (sendIntToSerial(RESPONSE_API_VERSION, API_VERSION)) {
                    onItemSent();
                }
                break;
            case 13: // SEND_VOLTAGE
                uint16_t voltage;
                voltage = readVoltage();
                if (voltage > VOLTAGE_ZERO_THRESHOLD) {
                    if (sendIntToSerial(RESPONSE_VOLTAGE, voltage)) {
                        onItemSent();
                    }
                } else {
                    onItemSent();
                }
                break;
            case 14: // SEND_THRESHOLD_SETUP_MODE
                if (send4BitsToSerial(RESPONSE_THRESHOLD_SETUP, thresholdSetupMode)) {
                    onItemSent();
                }
                break;
            // Below is a termination case, to notify that data for CONTROL_GET_ALL_DATA is over.
            // Must be the last item in the sequence!
            case 15: // SEND_END_SEQUENCE
                if (send4BitsToSerial(RESPONSE_END_SEQUENCE, 1)) {
                    onItemSent();
                    isSendingData = 0;
                    shouldSendSingleItem = 1;
                }
                break;

            //--------------------------------------------------------------------------------------
            // Here is the gap in sequence between items that are sent as response to
            // "CONTROL_GET_ALL_DATA" and items that are only sent individually
            //--------------------------------------------------------------------------------------

            case 100: // SEND_LAST_LAPTIMES
                uint8_t idx;
                idx = newLapIndex - lastLapsNotSent;
                if (lastLapsNotSent == 0) {
                    break;
                }
                if (sendLaptimeToSerial(RESPONSE_LAPTIME, idx, lapTimes[idx])) {
                    lastLapsNotSent--;
                    onItemSent();
                }
                break;
            case 101: // SEND_CURRENT_TIME
                if (sendLongToSerial(RESPONSE_TIME, millisUponRequest)) {
                    onItemSent();
                }
                break;
            case 102: // SEND_CURRENT_RSSI
                if (sendIntToSerial(RESPONSE_RSSI, rssi)) {
                    onItemSent();
                }
                break;
            default:
                isSendingData = 0;
                DEBUG_CODE(
                    digitalLow(serialTimerPin);
                );
        }
    }

    if (!isSendingData && !isQueueEmpty()) {
        uint8_t item = getFromSendQueue();
        if (item == SEND_ALL_DEVICE_STATE) {
            setupToSendAllItems();
        } else {
            setupToSendSingleItem(item);
        }
    }

    if (rssiMonitorInterval) {
        uint32_t ms = millis();
        if (ms - lastRssiMonitorReading >= rssiMonitorInterval) {
            addToSendQueue(SEND_CURRENT_RSSI);
            lastRssiMonitorReading = ms;
        }
    }

    if (thresholdSetupMode) {
        setupThreshold(RSSI_SETUP_NEXT_STEP);
    }

    if (isSoundEnabled && playSound) {
        if (playStartTime == 0) {
            tone(buzzerPin,curToneSeq[curToneIndex]);
            playStartTime = millis();
        }
        uint32_t dur = millis() - playStartTime;
        if (dur >= curToneSeq[curDurIndex]) {
            if (curDurIndex >= lastToneSeqIndex) {
                noTone(buzzerPin);
                playSound = 0;
            } else {
                curToneIndex += 2;
                curDurIndex += 2;
                tone(buzzerPin, curToneSeq[curToneIndex]);
                playStartTime = millis();
            }
        }
    }
}
// ----------------------------------------------------------------------------
uint8_t addToSendQueue(uint8_t item) {
    if (isSendQueueFull) {
        return 0; // couldn't add
    }
    sendQueue[sendQueueTail] = item;
    sendQueueTail++;
    if (sendQueueTail >= SEND_QUEUE_MAXLEN) {
        sendQueueTail = 0;
    }
    if (sendQueueTail == sendQueueHead) {
        isSendQueueFull = 1;
    }
    return 1; // successfully added
}
// ----------------------------------------------------------------------------
uint8_t getFromSendQueue() {
    // don't check for Q emptiness because it's expected to be done before this function call

    // if (isQueueEmpty()) {
    //     return 0; // couldn't read - queue is empty
    // }
    uint8_t result = sendQueue[sendQueueHead];
    sendQueueHead++;
    if (sendQueueHead >= SEND_QUEUE_MAXLEN) {
        sendQueueHead = 0;
    }
    isSendQueueFull = 0;

    return result;
}
// ----------------------------------------------------------------------------
uint8_t isQueueEmpty() {
    return (sendQueueHead == sendQueueTail && !isSendQueueFull);
}
// ----------------------------------------------------------------------------
void onItemSent() {
    if (shouldSendSingleItem) {
        isSendingData = 0;
    } else {
        sendStage++;
    }
}
// ----------------------------------------------------------------------------
void setupToSendAllItems() {
    isSendingData = 1;
    sendLapTimesIndex = 0;
    sendStage = 0;
    shouldSendSingleItem = 0;
}
// ----------------------------------------------------------------------------
void setupToSendSingleItem(uint8_t itemId) {
    isSendingData = 1;
    sendLapTimesIndex = 0;
    sendStage = itemId;
    shouldSendSingleItem = 1;
}
// ----------------------------------------------------------------------------
void handleSerialControlInput(uint8_t *controlData, uint8_t length) {
    uint8_t controlByte = controlData[0];
    uint8_t valueToSet;

    if (length > 3) { // set value commands
        switch(controlByte) {
            case CONTROL_RACE_MODE:
                valueToSet = TO_BYTE(controlData[1]);
                setRaceMode(valueToSet);
                addToSendQueue(SEND_RACE_MODE);
                isConfigured = 1;
                break;
            case CONTROL_WAIT_FIRST_LAP:
                valueToSet = TO_BYTE(controlData[1]);
                shouldWaitForFirstLap = valueToSet;
                playClickTones();
                addToSendQueue(SEND_LAP0_STATE);
                isConfigured = 1;
                break;
            case CONTROL_BAND:
                valueToSet = TO_BYTE(controlData[1]);
                setBand(valueToSet);
                playClickTones();
                addToSendQueue(SEND_BAND);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_CHANNEL:
                valueToSet = TO_BYTE(controlData[1]);
                setChannel(valueToSet);
                playClickTones();
                addToSendQueue(SEND_CHANNEL);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_FREQUENCY:
                frequency = setModuleFrequency(HEX_TO_UINT16(&controlData[1]));
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_RSSI_MON_INTERVAL:
                rssiMonitorInterval = setRssiMonitorInterval(HEX_TO_UINT16(&controlData[1]));
                lastRssiMonitorReading = 0;
                addToSendQueue(SEND_MON_INTERVAL);
                isConfigured = 1;
                break;
            case CONTROL_MIN_LAP_TIME:
                valueToSet = HEX_TO_BYTE(controlData[1], controlData[2]);
                setMinLap(valueToSet);
                playClickTones();
                addToSendQueue(SEND_MIN_LAP_TIME);
                isConfigured = 1;
                break;
            case CONTROL_SOUND:
                valueToSet = TO_BYTE(controlData[1]);
                isSoundEnabled = valueToSet;
                if (!isSoundEnabled) {
                    noTone(buzzerPin);
                }
                addToSendQueue(SEND_SOUND_STATE);
                playClickTones();
                isConfigured = 1;
                break;
            case CONTROL_THRESHOLD:
                setThresholdValue(HEX_TO_UINT16(&controlData[1]));
                addToSendQueue(SEND_THRESHOLD);
                isConfigured = 1;
                break;
            case CONTROL_TIME_ADJUSTMENT:
                timeAdjustment = HEX_TO_SIGNED_LONG(&controlData[1]);
                addToSendQueue(SEND_TIME_ADJUSTMENT);
                isConfigured = 1;
                break;
            case CONTROL_THRESHOLD_SETUP: // setup threshold using sophisticated algorithm
                valueToSet = TO_BYTE(controlData[1]);
                thresholdSetupMode = valueToSet;
                if (raceMode) { // don't run threshold setup in race mode because we don't calculate slowRssi in race mode, but it's needed for setup threshold algorithm
                    thresholdSetupMode = 0;
                }
                if (thresholdSetupMode) {
                    setupThreshold(RSSI_SETUP_INITIALIZE);
                } else {
                    playThresholdSetupStopTones();
                }
                addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
                break;
        }
    } else { // get value and other instructions
        switch (controlByte) {
            case CONTROL_GET_TIME:
                millisUponRequest = millis();
                addToSendQueue(SEND_TIME);
                break;
            case CONTROL_WAIT_FIRST_LAP:
                addToSendQueue(SEND_LAP0_STATE);
                break;
            case CONTROL_BAND:
                addToSendQueue(SEND_BAND);
                break;
            case CONTROL_CHANNEL:
                addToSendQueue(SEND_CHANNEL);
                break;
            case CONTROL_FREQUENCY:
                addToSendQueue(SEND_FREQUENCY);
                break;
            case CONTROL_RSSI_MON_INTERVAL:
                addToSendQueue(SEND_MON_INTERVAL);
                break;
            case CONTROL_RACE_MODE:
                addToSendQueue(SEND_RACE_MODE);
                break;
            case CONTROL_MIN_LAP_TIME:
                addToSendQueue(SEND_MIN_LAP_TIME);
                break;
            case CONTROL_SOUND:
                addToSendQueue(SEND_SOUND_STATE);
                break;
            case CONTROL_THRESHOLD:
                addToSendQueue(SEND_THRESHOLD);
                break;
            case CONTROL_GET_RSSI: // get current RSSI value
                addToSendQueue(SEND_CURRENT_RSSI);
                break;
            case CONTROL_GET_VOLTAGE: //get battery voltage
                addToSendQueue(SEND_VOLTAGE);
                break;
            case CONTROL_GET_ALL_DATA: // request all data
                addToSendQueue(SEND_ALL_DEVICE_STATE);
                break;
            case CONTROL_GET_API_VERSION: //get API version
                addToSendQueue(SEND_API_VERSION);
                break;
            case CONTROL_TIME_ADJUSTMENT:
                addToSendQueue(SEND_TIME_ADJUSTMENT);
                break;
            case CONTROL_THRESHOLD_SETUP: // get state of threshold setup process
                addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
                break;
            case CONTROL_GET_IS_CONFIGURED:
                addToSendQueue(SEND_IS_CONFIGURED);
                break;
        }
    }
}
// ----------------------------------------------------------------------------
void readSerialDataChunk () {
    // don't read anything if we have something not sent in proxyBuf
    if (proxyBufDataSize != 0) return;

    uint8_t availBytes = Serial.available();
    if (availBytes) {
        if (availBytes > READ_BUFFER_SIZE) {
            digitalHigh(ledPin);
        }

        uint8_t freeBufBytes = READ_BUFFER_SIZE - readBufFilledBytes;

        //reset buffer if we couldn't find delimiter in its contents in prev step
        if (freeBufBytes == 0) {
            readBufFilledBytes = 0;
            freeBufBytes = READ_BUFFER_SIZE;
        }

        //read minimum of "available to read" and "free place in buffer"
        uint8_t canGetBytes = availBytes > freeBufBytes ? freeBufBytes : availBytes;
        Serial.readBytes(&readBuf[readBufFilledBytes], canGetBytes);
        readBufFilledBytes += canGetBytes;
    }

    if (readBufFilledBytes) {
        //try finding a delimiter
        uint8_t foundIdx = 255;
        for (uint8_t i = 0; i < readBufFilledBytes; i++) {
            if (readBuf[i] == SERIAL_DATA_DELIMITER) {
                foundIdx = i;
                break;
            }
        }

        uint8_t shouldPassMsgFurther = 1;
        //if delimiter found then process the command or send it further
        if (foundIdx < READ_BUFFER_SIZE) {
            switch (readBuf[0]) {
                case 'R': //request to module (command or get/set value)
                    if (readBuf[1] == MODULE_ID_HEX) {
                        //process input targeted for this device
                        handleSerialControlInput(&readBuf[2], foundIdx);
                        shouldPassMsgFurther = 0;
                    }
                    else if (readBuf[1] == '*') {
                        //broadcast message. process in this module and pass further
                        handleSerialControlInput(&readBuf[2], foundIdx);
                    }
                    break;
                case 'N':  //enumerate modules
                    //process auto-enumeration request (save current number and pass next number further)
                    MODULE_ID_HEX = readBuf[1];
                    MODULE_ID = TO_BYTE(MODULE_ID_HEX);
                    readBuf[1] = TO_HEX(MODULE_ID + 1);
                    break;
            }
            if (shouldPassMsgFurther) {
                memmove(proxyBuf, readBuf, foundIdx);
                proxyBufDataSize = foundIdx;
            }
            //remove processed portion of data
            memmove(readBuf, &readBuf[foundIdx+1], readBufFilledBytes - foundIdx+1);
            readBufFilledBytes -= foundIdx+1;
        }
    }
}
// ----------------------------------------------------------------------------
void sendProxyDataChunk () {
    if (proxyBufDataSize && Serial.availableForWrite() > proxyBufDataSize) {
        Serial.write(proxyBuf, proxyBufDataSize);
        Serial.write(SERIAL_DATA_DELIMITER);

        proxyBufDataSize = 0;
    }
}
// ----------------------------------------------------------------------------
void setRaceMode(uint8_t mode) {
    if (mode == 0) { // stop race
        raceMode = 0;
        newLapIndex = 0;
        playEndRaceTones();
    } else { // start race in specified mode
        raceMode = mode;
        raceStartTime = millis();
        lastMilliseconds = raceStartTime;
        newLapIndex = 0;
        if (thresholdSetupMode) {
            thresholdSetupMode = 0; // safety measure: stop setting threshold upon race start to avoid losing time there
            addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
        }
        playStartRaceTones();
    }
}
// ----------------------------------------------------------------------------
void setMinLap(uint8_t mlt) {
    if (mlt >= MIN_MIN_LAP_TIME && mlt <= MAX_MIN_LAP_TIME) {
        minLapTime = mlt;
    }
}
// ----------------------------------------------------------------------------
void setChannel(uint8_t channel) {
    if (channel >= 0 && channel <= 7) {
        channelIndex = channel;
        frequency = setModuleChannel(channelIndex, bandIndex);
    }
}
// ----------------------------------------------------------------------------
void setBand(uint8_t band) {
    if (band >= 0 && band <= MAX_BAND) {
        bandIndex = band;
        frequency = setModuleChannel(channelIndex, bandIndex);
    }
}
// ----------------------------------------------------------------------------
void setupThreshold(uint8_t phase) {
    // this process assumes the following:
    // 1. before the process all VTXs are turned ON, but are distant from the Chorus device, so that Chorus sees the "background" rssi values only
    // 2. once the setup process is initiated by Chorus operator, all pilots walk towards the Chorus device
    // 3. setup process starts tracking top rssi values
    // 4. as pilots come closer, rssi should rise above the value defined by RISE_RSSI_THRESHOLD_PERCENT
    // 5. after that setup expects rssi to fall from the reached top, down by FALL_RSSI_THRESHOLD_PERCENT
    // 6. after the rssi falls, the top recorded value (decreased by TOP_RSSI_DECREASE_PERCENT) is set as a threshold

    // time constant for accumulation filter: higher value => more delay
    // value of 20 should give about 100 readings before value reaches the settled rssi
    // don't make it bigger than 2000 to avoid overflow of accumulatedShiftedRssi
    #define ACCUMULATION_TIME_CONSTANT 150
    #define MILLIS_BETWEEN_ACCU_READS 10 // artificial delay between rssi reads to slow down the accumulation
    #define TOP_RSSI_DECREASE_PERCENT 10 // decrease top value by this percent using diff between low and high as a base
    #define RISE_RSSI_THRESHOLD_PERCENT 25 // rssi value should pass this percentage above low value to continue finding the peak and further fall down of rssi
    #define FALL_RSSI_THRESHOLD_PERCENT 50 // rssi should fall below this percentage of diff between high and low to finalize setup of the threshold

    static uint16_t rssiLow;
    static uint16_t rssiHigh;
    static uint16_t rssiHighEnoughForMonitoring;
    static uint32_t accumulatedShiftedRssi; // accumulates rssi slowly; contains multiplied rssi value for better accuracy
    static uint32_t lastRssiAccumulationTime;

    if (!thresholdSetupMode) return; // just for safety, normally it's controlled outside

    if (phase == RSSI_SETUP_INITIALIZE) {
        // initialization step
        playThresholdSetupStartTones();
        thresholdSetupMode = 1;
        rssiLow = slowRssi; // using slowRssi to avoid catching random current rssi
        rssiHigh = rssiLow;
        accumulatedShiftedRssi = rssiLow * ACCUMULATION_TIME_CONSTANT; // multiply to prevent loss in accuracy
        rssiHighEnoughForMonitoring = rssiLow + rssiLow * RISE_RSSI_THRESHOLD_PERCENT / 100;
        lastRssiAccumulationTime = millis();
    } else {
        // active phase step (searching for high value and fall down)
        if (thresholdSetupMode == 1) {
            // in this phase of the setup we are tracking rssi growth until it reaches the predefined percentage from low

            // searching for peak; using slowRssi to avoid catching sudden random peaks
            if (slowRssi > rssiHigh) {
                rssiHigh = slowRssi;
            }

            // since filter runs too fast, we have to introduce a delay between subsequent readings of filter values
            uint32_t curTime = millis();
            if ((curTime - lastRssiAccumulationTime) > MILLIS_BETWEEN_ACCU_READS) {
                lastRssiAccumulationTime = curTime;
                // this is actually a filter with a delay determined by ACCUMULATION_TIME_CONSTANT
                accumulatedShiftedRssi = rssi  + (accumulatedShiftedRssi * (ACCUMULATION_TIME_CONSTANT - 1) / ACCUMULATION_TIME_CONSTANT);
            }

            uint16_t accumulatedRssi = accumulatedShiftedRssi / ACCUMULATION_TIME_CONSTANT; // find actual rssi from multiplied value

            if (accumulatedRssi > rssiHighEnoughForMonitoring) {
                thresholdSetupMode = 2;
                accumulatedShiftedRssi = rssiHigh * ACCUMULATION_TIME_CONSTANT;
                playThresholdSetupMiddleTones();
                addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
            }
        } else {
            // in this phase of the setup we are tracking highest rssi and expect it to fall back down so that we know that the process is complete

            // continue searching for peak; using slowRssi to avoid catching sudden random peaks
            if (slowRssi > rssiHigh) {
                rssiHigh = slowRssi;
                accumulatedShiftedRssi = rssiHigh * ACCUMULATION_TIME_CONSTANT; // set to highest found rssi
            }

            // since filter runs too fast, we have to introduce a delay between subsequent readings of filter values
            uint32_t curTime = millis();
            if ((curTime - lastRssiAccumulationTime) > MILLIS_BETWEEN_ACCU_READS) {
                lastRssiAccumulationTime = curTime;
                // this is actually a filter with a delay determined by ACCUMULATION_TIME_CONSTANT
                accumulatedShiftedRssi = rssi  + (accumulatedShiftedRssi * (ACCUMULATION_TIME_CONSTANT - 1) / ACCUMULATION_TIME_CONSTANT );
            }
            uint16_t accumulatedRssi = accumulatedShiftedRssi / ACCUMULATION_TIME_CONSTANT;

            uint16_t rssiLowEnoughForSetup = rssiHigh - (rssiHigh - rssiLow) * FALL_RSSI_THRESHOLD_PERCENT / 100;
            if (accumulatedRssi < rssiLowEnoughForSetup) {
                rssiThreshold = rssiHigh - ((rssiHigh - rssiLow) * TOP_RSSI_DECREASE_PERCENT) / 100;
                thresholdSetupMode = 0;
                isConfigured = 1;
                playThresholdSetupDoneTones();
                addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
                addToSendQueue(SEND_THRESHOLD);
            }
        }
    }
}

// ----------------------------------------------------------------------------
void setThresholdValue(uint16_t threshold) {
    // stop the "setting threshold algorithm" to avoid overwriting the explicitly set value
    if (thresholdSetupMode) {
        thresholdSetupMode = 0;
        addToSendQueue(SEND_THRESHOLD_SETUP_MODE);
    }
    rssiThreshold = threshold;
    if (threshold != 0) {
        playClickTones();
    } else {
        playClearThresholdTones();
    }
}
// ----------------------------------------------------------------------------
uint16_t setRssiMonitorInterval(uint16_t interval) {
    // valid values are: zero and others above MIN_RSSI_MONITOR_INTERVAL
    return (interval > 0 && interval < MIN_RSSI_MONITOR_INTERVAL) ? MIN_RSSI_MONITOR_INTERVAL : interval;
}
// ----------------------------------------------------------------------------
uint16_t getFilteredRSSI() {
    rssiArr[0] = readRSSI();

    // several-pass filter (need several passes because of integer artithmetics)
    // it reduces possible max value by 1 with each iteration.
    // e.g. if max rssi is 300, then after 5 filter stages it won't be greater than 295
    for(uint8_t i=1; i<=FILTER_ITERATIONS; i++) {
        rssiArr[i] = (rssiArr[i-1] + rssiArr[i]) >> 1;
    }

    return rssiArr[FILTER_ITERATIONS];
}
// ----------------------------------------------------------------------------
// this is just a digital filter function
uint16_t getSlowChangingRSSI() {
    #define TIME_DELAY_CONSTANT 1000 // this is a filtering constant that regulates both depth of filtering and delay at the same time
    static uint32_t slowRssiMultiplied;

    slowRssiMultiplied = rssi  + (slowRssiMultiplied * (TIME_DELAY_CONSTANT - 1) / TIME_DELAY_CONSTANT );
    return slowRssiMultiplied / TIME_DELAY_CONSTANT;
}
// ----------------------------------------------------------------------------
void sortArray(uint16_t a[], uint16_t size) {
    for(uint16_t i=0; i<(size-1); i++) {
        for(uint16_t j=0; j<(size-(i+1)); j++) {
                if(a[j] > a[j+1]) {
                    uint16_t t = a[j];
                    a[j] = a[j+1];
                    a[j+1] = t;
                }
        }
    }
}
// ----------------------------------------------------------------------------
uint16_t getMedian(uint16_t a[], uint16_t size) {
    return a[size/2];
}
// ----------------------------------------------------------------------------
void gen_rising_edge(int pin) {
    digitalHigh(pin); //this will open mosfet and pull the RasPi pin to GND
    delayMicroseconds(10);
    digitalLow(pin); // this will close mosfet and pull the RasPi pin to 3v3 -> Rising Edge
}
// ----------------------------------------------------------------------------
uint16_t readRSSI() {
    int rssiA = 0;

    for (uint8_t i = 0; i < RSSI_READS; i++) {
        rssiA += analogRead(rssiPinA);
    }

    rssiA = rssiA/RSSI_READS; // average of RSSI_READS readings
    return rssiA;
}
// ----------------------------------------------------------------------------
uint16_t readVoltage() {
    int voltageA = 0;

    for (uint8_t i = 0; i < VOLTAGE_READS; i++) {
        voltageA += analogRead(voltagePinA);
    }

    voltageA = voltageA/VOLTAGE_READS; // average of RSSI_READS readings
    return voltageA;
}
