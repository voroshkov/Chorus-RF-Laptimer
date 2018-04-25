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
    when CONTROL_DATA_REQUEST is being processed, i.e. all data items are sent in sequence,
    and either external or external logic decides to send an individual item,
    then the latter just breaks execution of CONTROL_DATA_REQUEST and data is not sent completely
    This behavior should not occur in case of normal send queue processing.

TODO: there's possible optimization in send queue:
    remove already existing items from queue
*/

#define API_VERSION 3 // version number to be increased with each API change (int16)

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

// input control byte constants
#define CONTROL_START_RACE      'R'
#define CONTROL_START_RACE_ABS  'P' // race with absolute laps timing
#define CONTROL_END_RACE        'r'
#define CONTROL_DEC_MIN_LAP     'm'
#define CONTROL_INC_MIN_LAP     'M'
#define CONTROL_DEC_CHANNEL     'c'
#define CONTROL_INC_CHANNEL     'C'
#define CONTROL_DEC_THRESHOLD   't'
#define CONTROL_INC_THRESHOLD   'T'
#define CONTROL_SET_THRESHOLD   'S' // it either triggers or sets an uint16 depending on command length
#define CONTROL_SET_SOUND       'D'
#define CONTROL_DATA_REQUEST    'A'
#define CONTROL_INC_BAND        'B'
#define CONTROL_DEC_BAND        'b'
#define CONTROL_START_CALIBRATE 'I'
#define CONTROL_END_CALIBRATE   'i'
#define CONTROL_MONITOR_ON      'V'
#define CONTROL_MONITOR_OFF     'v'
#define CONTROL_SET_SKIP_LAP0   'F'
#define CONTROL_GET_VOLTAGE     'Y'
#define CONTROL_GET_RSSI        'E'
#define CONTROL_GET_API_VERSION '#'
// input control byte constants for uint8 "set value" commands
#define CONTROL_SET_MIN_LAP     'L'
#define CONTROL_SET_CHANNEL     'H'
#define CONTROL_SET_BAND        'N'
// input control byte constants for uint16 "set value" commands
#define CONTROL_SET_FREQUENCY   'Q'
#define CONTROL_SET_MON_DELAY   'd'

// output id byte constants
#define RESPONSE_CHANNEL        'C'
#define RESPONSE_RACE_STATE     'R'
#define RESPONSE_MIN_LAP_TIME   'M'
#define RESPONSE_THRESHOLD      'T'
#define RESPONSE_CURRENT_RSSI   'S'
#define RESPONSE_LAPTIME        'L'
#define RESPONSE_SOUND_STATE    'D'
#define RESPONSE_BAND           'B'
#define RESPONSE_CALIBR_TIME    'I'
#define RESPONSE_CALIBR_STATE   'i'
#define RESPONSE_MONITOR_STATE  'V'
#define RESPONSE_LAP0_STATE     'F'
#define RESPONSE_END_SEQUENCE   'X'
#define RESPONSE_IS_CONFIGURED  'P'
#define RESPONSE_VOLTAGE        'Y'
#define RESPONSE_FREQUENCY      'Q'
#define RESPONSE_MONITOR_DELAY  'd'
#define RESPONSE_API_VERSION    '#'

// send item byte constants
// Must correspond to sequence of numbers used in "send data" switch statement
// Subsequent items starting from 0 participate in "send all data" response
#define SEND_CHANNEL        0
#define SEND_RACE_STATE     1
#define SEND_MIN_LAP_TIME   2
#define SEND_THRESHOLD      3
#define SEND_ALL_LAPTIMES   4
#define SEND_SOUND_STATE    5
#define SEND_BAND           6
#define SEND_CALIBR_STATE   7
#define SEND_MONITOR_STATE  8
#define SEND_LAP0_STATE     9
#define SEND_IS_CONFIGURED  10
#define SEND_FREQUENCY      11
#define SEND_MONITOR_DELAY  12
#define SEND_API_VERSION    13
#define SEND_END_SEQUENCE   14
// following items don't participate in "send all items" response
#define SEND_LAST_LAPTIMES  100
#define SEND_CALIBR_TIME    101
#define SEND_CURRENT_RSSI   102
#define SEND_VOLTAGE        103
// special item that sends all subsequent items from 0 (see above)
#define SEND_ALL_DEVICE_STATE 255

//----- RSSI --------------------------------------
#define FILTER_ITERATIONS 0 // software filtering iterations; set 0 - if filtered in hardware; set 5 - if not
uint16_t rssiArr[FILTER_ITERATIONS + 1];
uint16_t rssiThreshold = 190;
uint16_t rssi;

#define RSSI_MAX 1024
#define RSSI_MIN 0
#define MAGIC_THRESHOLD_REDUCE_CONSTANT 2
#define THRESHOLD_ARRAY_SIZE  100
uint16_t rssiThresholdArray[THRESHOLD_ARRAY_SIZE];

#define DEFAULT_RSSI_MONITOR_DELAY_CYCLES 1000 //each 100ms, if cycle takes 100us
#define MIN_RSSI_MONITOR_DELAY_CYCLES 10 //each 1ms, if cycle takes 100us, to prevent loss of communication
uint16_t rssiMonitorDelayCycles = DEFAULT_RSSI_MONITOR_DELAY_CYCLES;

#define RSSI_SETUP_INITIALIZE 0
#define RSSI_SETUP_NEXT_STEP 1

//----- Voltage monitoring -------------------------
#define VOLTAGE_READS 3 //get average of VOLTAGE_READS readings

// analog readings less than VOLTAGE_ZERO_THRESHOLD value won't be sent.
// This way entire chorus device will send voltages only from devices that are attached to LiPo
// So if single Solo device has LiPo attached, then broadcast voltage request to
// entire Chorus device will produce a single voltage response.
#define VOLTAGE_ZERO_THRESHOLD 100

uint16_t voltage = 0;

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
#define MAX_MIN_LAP_TIME 60 //seconds
uint8_t minLapTime = 5; //seconds
#define MAX_LAPS 100
uint32_t lapTimes[MAX_LAPS];

//----- Calibration -------------------------------
uint8_t isCalibrated = 0;
uint32_t calibrationMilliseconds = 0;

// Usage of signed int calibration constant:
// calibratedMs = readMs + readMs/timeCalibrationConst
int32_t timeCalibrationConst = 0;

//----- other globals------------------------------
uint8_t allowEdgeGeneration = 0;
uint8_t channelIndex = 0;
uint8_t bandIndex = 0;
uint8_t isRaceStarted = 0;
uint8_t raceType = 0; // type 1: lap times are counted as absolute for each lap; type 2: lap times are relative to the race start (sum of previous absolute lap times);
uint8_t isSoundEnabled = 1;
uint8_t isConfigured = 0; //changes to 1 if any input changes the state of the device. it will mean that externally stored preferences should not be applied
uint8_t rssiMonitor = 0;
uint8_t newLapIndex = 0;
uint8_t shouldSkipFirstLap = 1; //start table is before the laptimer, so first lap is not a full-fledged lap (i.e. don't respect min-lap-time for the very first lap)
uint8_t isSendingData = 0;
uint8_t sendStage = 0;
uint8_t sendLapTimesIndex = 0;
uint8_t sendLastLapIndex = 0;
uint8_t shouldSendSingleItem = 0;
uint8_t lastLapsNotSent = 0;
uint16_t rssiMonitorDelayExpiration = 0;
uint16_t frequency = 0;
uint8_t isSettingThreshold = 0;

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
        pinMode(sequencePin, OUTPUT);
    );
}
// ----------------------------------------------------------------------------
void loop() {
    DEBUG_CODE(
        digitalToggle(loopTimerPin);
    );

    rssi = getFilteredRSSI();

    // check rssi threshold to identify when drone finishes the lap
    if (rssiThreshold > 0) { // threshold = 0 means that we don't check rssi values
        if(rssi > rssiThreshold) { // rssi above the threshold - drone is near
            if (allowEdgeGeneration) {  // we haven't fired event for this drone proximity case yet
                allowEdgeGeneration = 0;
                gen_rising_edge(pinRaspiInt);  //generate interrupt for EasyRaceLapTimer software

                uint32_t now = millis();
                uint32_t diff = now - lastMilliseconds; //time diff with the last lap (or with the race start)
                if (timeCalibrationConst) {
                    diff = diff + (int32_t)diff/timeCalibrationConst;
                }
                if (isRaceStarted) { // if we're within the race, then log lap time
                    if (diff > minLapTime*1000 || (shouldSkipFirstLap && newLapIndex == 0)) { // if minLapTime haven't passed since last lap, then it's probably false alarm
                        // digitalLow(ledPin);
                        if (newLapIndex < MAX_LAPS-1) { // log time only if there are slots available
                            if (raceType == 1) {
                                // for the raceType 1 count time spent for each lap
                                lapTimes[newLapIndex] = diff;
                            } else {
                                // for the raceType 2 count times relative to the race start (ever-growing with each new lap within the race)
                                uint32_t diffStart = now - raceStartTime;
                                if (timeCalibrationConst) {
                                    diffStart = diffStart + (int32_t)diffStart/timeCalibrationConst;
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
            allowEdgeGeneration = 1; // we're below the threshold, be ready to catch another case
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
            case 1: // SEND_RACE_STATE
                if (send4BitsToSerial(RESPONSE_RACE_STATE, isRaceStarted)) {
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
                if (send4BitsToSerial(RESPONSE_SOUND_STATE, isSoundEnabled)) {
                    onItemSent();
                }
                break;
            case 6: // SEND_BAND
                if (send4BitsToSerial(RESPONSE_BAND, bandIndex)) {
                    onItemSent();
                }
                break;
            case 7: // SEND_CALIBR_STATE
                if (send4BitsToSerial(RESPONSE_CALIBR_STATE, isCalibrated)) {
                    onItemSent();
                }
                break;
            case 8: // SEND_MONITOR_STATE
                if (send4BitsToSerial(RESPONSE_MONITOR_STATE, rssiMonitor)) {
                    onItemSent();
                }
                break;
            case 9: // SEND_LAP0_STATE
                if (send4BitsToSerial(RESPONSE_LAP0_STATE, shouldSkipFirstLap)) {
                    onItemSent();
                }
                break;
            case 10: // SEND_IS_CONFIGURED
                if (send4BitsToSerial(RESPONSE_IS_CONFIGURED, isConfigured)) {
                    onItemSent();
                }
                break;
            case 11: // SEND_FREQUENCY
                if (sendIntToSerial(RESPONSE_FREQUENCY, frequency)) {
                    onItemSent();
                }
                break;
            case 12: // SEND_MONITOR_DELAY
                if (sendIntToSerial(RESPONSE_MONITOR_DELAY, rssiMonitorDelayCycles)) {
                    onItemSent();
                }
                break;
            case 13: // SEND_API_VERSION
                if (sendIntToSerial(RESPONSE_API_VERSION, API_VERSION)) {
                    onItemSent();
                }
                break;
            // Below is a termination case, to notify that data for CONTROL_DATA_REQUEST is over.
            // Must be the last item in the sequence!
            case 14: // SEND_END_SEQUENCE
                if (send4BitsToSerial(RESPONSE_END_SEQUENCE, 1)) {
                    onItemSent();
                    isSendingData = 0;
                    shouldSendSingleItem = 1;
                }
                break;

            //--------------------------------------------------------------------------------------
            // Here is the gap in sequence between items that are sent as response to
            // "CONTROL_DATA_REQUEST" and items that are only sent individually
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
            case 101: // SEND_CALIBR_TIME
                if (sendLongToSerial(RESPONSE_CALIBR_TIME, calibrationMilliseconds)) {
                    onItemSent();
                }
                break;
            case 102: // SEND_CURRENT_RSSI
                if (sendIntToSerial(RESPONSE_CURRENT_RSSI, rssi)) {
                    onItemSent();
                }
                break;
            case 103: // SEND_VOLTAGE
                if (voltage > VOLTAGE_ZERO_THRESHOLD) {
                    if (sendIntToSerial(RESPONSE_VOLTAGE, voltage)) {
                        onItemSent();
                    }
                } else {
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

    if (rssiMonitor) {
        rssiMonitorDelayExpiration++;
        if (rssiMonitorDelayExpiration >= rssiMonitorDelayCycles) {
            addToSendQueue(SEND_CURRENT_RSSI);
            rssiMonitorDelayExpiration = 0;
        }
    }

    if (isSettingThreshold) {
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

    if (length > 3) {
        switch(controlByte) {
            case CONTROL_SET_CHANNEL:
                valueToSet = TO_BYTE(controlData[1]);
                setChannel(valueToSet);
                playClickTones();
                addToSendQueue(SEND_CHANNEL);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_SET_BAND:
                valueToSet = TO_BYTE(controlData[1]);
                setBand(valueToSet);
                playClickTones();
                addToSendQueue(SEND_BAND);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_SET_MIN_LAP:
                valueToSet = HEX_TO_BYTE(controlData[1], controlData[2]);
                setMinLap(valueToSet);
                playClickTones();
                addToSendQueue(SEND_MIN_LAP_TIME);
                isConfigured = 1;
                break;
            case CONTROL_SET_THRESHOLD: // set threshold
                setThresholdValue(HEX_TO_UINT16(&controlData[1]));
                addToSendQueue(SEND_THRESHOLD);
                isConfigured = 1;
                break;
            case CONTROL_SET_FREQUENCY: // set frequency
                frequency = setModuleFrequency(HEX_TO_UINT16(&controlData[1]));
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_SET_MON_DELAY: // set frequency
                rssiMonitorDelayCycles = setRssiMonitorDelay(HEX_TO_UINT16(&controlData[1]));
                addToSendQueue(SEND_MONITOR_DELAY);
                isConfigured = 1;
                break;
        }
    } else {
        switch (controlByte) {
            case CONTROL_START_RACE: // start race
                raceStartTime = millis();
                lastMilliseconds = raceStartTime;
                DEBUG_CODE(
                    digitalHigh(serialTimerPin);
                );
                newLapIndex = 0;
                isRaceStarted = 1;
                raceType = 1;
                allowEdgeGeneration = 0;
                playStartRaceTones();
                addToSendQueue(SEND_RACE_STATE);
                isConfigured = 1;
                break;
            case CONTROL_START_RACE_ABS: // start race with absolute laps timing
                raceStartTime = millis();
                lastMilliseconds = raceStartTime;
                newLapIndex = 0;
                isRaceStarted = 1;
                raceType = 2;
                allowEdgeGeneration = 0;
                playStartRaceTones();
                addToSendQueue(SEND_RACE_STATE);
                isConfigured = 1;
                break;
            case CONTROL_END_CALIBRATE: // end calibration
                calibrationMilliseconds = millis() - calibrationMilliseconds;
                addToSendQueue(SEND_CALIBR_TIME);
                break;
            case CONTROL_START_CALIBRATE: // start calibration
                calibrationMilliseconds = millis();
                break;
            case CONTROL_END_RACE: // end race
                isRaceStarted = 0;
                newLapIndex = 0;
                playEndRaceTones();
                addToSendQueue(SEND_RACE_STATE);
                isConfigured = 1;
                break;
            case CONTROL_GET_RSSI: // get current RSSI value
                addToSendQueue(SEND_CURRENT_RSSI);
                break;
            case CONTROL_DEC_MIN_LAP: // decrease minLapTime
                decMinLap();
                playClickTones();
                addToSendQueue(SEND_MIN_LAP_TIME);
                isConfigured = 1;
                break;
            case CONTROL_INC_MIN_LAP: // increase minLapTime
                incMinLap();
                playClickTones();
                addToSendQueue(SEND_MIN_LAP_TIME);
                isConfigured = 1;
                break;
            case CONTROL_DEC_CHANNEL: // decrease channel
                decChannel();
                playClickTones();
                addToSendQueue(SEND_CHANNEL);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_INC_CHANNEL: // increase channel
                incChannel();
                playClickTones();
                addToSendQueue(SEND_CHANNEL);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_DEC_BAND: // decrease band
                decBand();
                playClickTones();
                addToSendQueue(SEND_BAND);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_INC_BAND: // increase channel
                incBand();
                playClickTones();
                addToSendQueue(SEND_BAND);
                addToSendQueue(SEND_FREQUENCY);
                isConfigured = 1;
                break;
            case CONTROL_DEC_THRESHOLD: // decrease threshold
                decThreshold();
                playClickTones();
                addToSendQueue(SEND_THRESHOLD);
                isConfigured = 1;
                break;
            case CONTROL_INC_THRESHOLD: // increase threshold
                incThreshold();
                playClickTones();
                addToSendQueue(SEND_THRESHOLD);
                isConfigured = 1;
                break;
            case CONTROL_SET_THRESHOLD: // set threshold
                setOrDropThreshold();

                break;
            case CONTROL_SET_SOUND: // set sound
                isSoundEnabled = !isSoundEnabled;
                if (!isSoundEnabled) {
                    noTone(buzzerPin);
                }
                addToSendQueue(SEND_SOUND_STATE);
                playClickTones();
                isConfigured = 1;
                break;
            case CONTROL_MONITOR_ON: // start RSSI monitor
                rssiMonitor = 1;
                rssiMonitorDelayExpiration = 0;
                addToSendQueue(SEND_MONITOR_STATE);
                // don't play tones here because it suppresses race tone when used simultaneously
                // playClickTones();
                break;
            case CONTROL_MONITOR_OFF: // stop RSSI monitor
                rssiMonitor = 0;
                addToSendQueue(SEND_MONITOR_STATE);
                // don't play tones here because it suppresses race tone when used simultaneously
                // playClickTones();
                break;
            case CONTROL_SET_SKIP_LAP0: // set valid first lap
                shouldSkipFirstLap = !shouldSkipFirstLap;
                addToSendQueue(SEND_LAP0_STATE);
                playClickTones();
                isConfigured = 1;
                break;
            case CONTROL_GET_VOLTAGE: //get battery voltage
                voltage = readVoltage();
                addToSendQueue(SEND_VOLTAGE);
                break;
            case CONTROL_DATA_REQUEST: // request all data
                addToSendQueue(SEND_ALL_DEVICE_STATE);
                break;
            case CONTROL_GET_API_VERSION: //get API version
                addToSendQueue(SEND_API_VERSION);
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
                case 'R': //read data from module
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
                case 'C': //read calibration data for current module
                    if (readBuf[1] == MODULE_ID_HEX) {
                        timeCalibrationConst = HEX_TO_SIGNED_LONG(&readBuf[2]);
                        isCalibrated = 1;
                        shouldPassMsgFurther = 0;
                        addToSendQueue(SEND_CALIBR_STATE);
                        isConfigured = 1;
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
void decMinLap() {
    if (minLapTime > MIN_MIN_LAP_TIME) {
        minLapTime--;
    }
}
// ----------------------------------------------------------------------------
void incMinLap() {
    if (minLapTime < MAX_MIN_LAP_TIME) {
        minLapTime++;
    }
}
// ----------------------------------------------------------------------------
void setMinLap(uint8_t mlt) {
    if (mlt >= MIN_MIN_LAP_TIME && mlt <= MAX_MIN_LAP_TIME) {
        minLapTime = mlt;
    }
}
// ----------------------------------------------------------------------------
void incChannel() {
    if (channelIndex < 7) {
        channelIndex++;
    }
    frequency = setModuleChannel(channelIndex, bandIndex);
}
// ----------------------------------------------------------------------------
void decChannel() {
    if (channelIndex > 0) {
        channelIndex--;
    }
    frequency = setModuleChannel(channelIndex, bandIndex);
}
// ----------------------------------------------------------------------------
void setChannel(uint8_t channel) {
    if (channel >= 0 && channel <= 7) {
        channelIndex = channel;
        frequency = setModuleChannel(channelIndex, bandIndex);
    }
}
// ----------------------------------------------------------------------------
void incBand() {
    if (bandIndex < MAX_BAND) {
        bandIndex++;
    }
    frequency = setModuleChannel(channelIndex, bandIndex);
}
// ----------------------------------------------------------------------------
void decBand() {
    if (bandIndex > 0) {
        bandIndex--;
    }
    frequency = setModuleChannel(channelIndex, bandIndex);
}
// ----------------------------------------------------------------------------
void setBand(uint8_t band) {
    if (band >= 0 && band <= MAX_BAND) {
        bandIndex = band;
        frequency = setModuleChannel(channelIndex, bandIndex);
    }
}
// ----------------------------------------------------------------------------
void incThreshold() {
    if (rssiThreshold < RSSI_MAX) {
        rssiThreshold++;
    }
}
// ----------------------------------------------------------------------------
void decThreshold() {
    if (rssiThreshold > RSSI_MIN) {
        rssiThreshold--;
    }
}
// ----------------------------------------------------------------------------
void setOrDropThreshold() {
    if (rssiThreshold == 0) {
        // uint16_t median;
        // for(uint8_t i=0; i < THRESHOLD_ARRAY_SIZE; i++) {
        //     rssiThresholdArray[i] = getFilteredRSSI();
        // }
        // sortArray(rssiThresholdArray, THRESHOLD_ARRAY_SIZE);
        // median = getMedian(rssiThresholdArray, THRESHOLD_ARRAY_SIZE);
        // if (median > MAGIC_THRESHOLD_REDUCE_CONSTANT) {
        //     rssiThreshold = median - MAGIC_THRESHOLD_REDUCE_CONSTANT;
        //     playSetThresholdTones();
        // }
        isSettingThreshold = 1;
        setupThreshold(RSSI_SETUP_INITIALIZE);
    }
    else {
        rssiThreshold = 0;
        isSettingThreshold = 0;
        isConfigured = 1;
        addToSendQueue(SEND_THRESHOLD);
        playClearThresholdTones();
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
    #define ACCUMULATION_TIME_CONSTANT 100
    #define MILLIS_BETWEEN_ACCU_READS 10 // artificial delay between rssi reads to slow down the accumulation
    #define TOP_RSSI_DECREASE_PERCENT 5 // decrease top value by this percent using diff between low and high as a base
    #define RISE_RSSI_THRESHOLD_PERCENT 20 // rssi value should pass this percentage above low value to continue finding the peak and further fall down of rssi
    #define FALL_RSSI_THRESHOLD_PERCENT 50 // rssi should fall below this percentage of diff between high and low to finalize setup of the threshold

    static uint8_t rssiSetupPhase;
    static uint16_t rssiLow;
    static uint16_t rssiHigh;
    static uint16_t rssiHighEnoughForMonitoring;
    static uint32_t accumulatedShiftedRssi; // accumulates rssi slowly; contains multiplied rssi value for better accuracy
    static uint32_t lastRssiAccumulationTime;

    if (!isSettingThreshold) return; // just for safety, normally it's controlled outside

    if (phase == RSSI_SETUP_INITIALIZE) {
        // initialization step
        playThresholdSetupStartTones();
        rssiThreshold = 0xFFFF; // just to make it clearable by setThreshold function
        isSettingThreshold = 1;
        rssiSetupPhase = 0;
        rssiLow = rssi;
        rssiHigh = rssi;
        accumulatedShiftedRssi = rssi * ACCUMULATION_TIME_CONSTANT; // multiply to prevent loss in accuracy
        rssiHighEnoughForMonitoring = rssiLow + rssiLow * RISE_RSSI_THRESHOLD_PERCENT / 100;
        lastRssiAccumulationTime = millis();
    } else {
        // active phase step (searching for high value and fall down)
        if (rssiSetupPhase == 0) {
            // in this phase of the setup we are tracking rssi growth until it reaches the predefined percentage from low

            // searching for peak
            if (rssi > rssiHigh) {
                rssiHigh = rssi;
            }

            // since filter runs too fast, we have to introduce a delay between subsequent readings of filter values
            uint32_t curTime = millis();
            if ((curTime - lastRssiAccumulationTime) > MILLIS_BETWEEN_ACCU_READS) {
                lastRssiAccumulationTime = curTime;
                // this is actually a filter with a delay determined by ACCUMULATION_TIME_CONSTANT
                accumulatedShiftedRssi = rssi  + (accumulatedShiftedRssi * (ACCUMULATION_TIME_CONSTANT - 1) / ACCUMULATION_TIME_CONSTANT );
            }

            uint16_t accumulatedRssi = accumulatedShiftedRssi / ACCUMULATION_TIME_CONSTANT; // find actual rssi from multiplied value

            if (accumulatedRssi > rssiHighEnoughForMonitoring) {
                rssiSetupPhase = 1;
                accumulatedShiftedRssi = rssiHigh * ACCUMULATION_TIME_CONSTANT;
                playThresholdSetupMiddleTones();
            }
        } else {
            // in this phase of the setup we are tracking highest rssi and expect it to fall back down so that we know that the process is complete

            if (rssi > rssiHigh) {
                rssiHigh = rssi;
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
                isSettingThreshold = 0;
                isConfigured = 1;
                playThresholdSetupDoneTones();
                addToSendQueue(SEND_THRESHOLD);
            }
        }
    }
}

// ----------------------------------------------------------------------------
void setThresholdValue(uint16_t threshold) {
    rssiThreshold = threshold;
    if (threshold != 0) {
        playSetThresholdTones();
    } else {
        playClearThresholdTones();
    }
}
// ----------------------------------------------------------------------------
uint16_t setRssiMonitorDelay(uint16_t delay) {
    return delay < MIN_RSSI_MONITOR_DELAY_CYCLES ? MIN_RSSI_MONITOR_DELAY_CYCLES : delay;
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
