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

//----- global variables for tones generation --------------
uint8_t playSound = 0;

uint16_t *curToneSeq;
uint32_t playStartTime;
uint8_t curToneSeqLength = 0;
uint8_t curToneIndex = 0;
uint8_t curDurIndex = 1;
uint8_t lastToneSeqIndex;

//----- buzzer tone sequences ------------------------------
// pairs of [frequency(Hz), duration(ms), ...]
// NOTES: 523, 587, 659, 698, 784, 880, 988, 1046

#define TONE_SEQ_DETECT_LEN 8
uint16_t toneSeq_Detect [TONE_SEQ_DETECT_LEN] = { 950, 50, 1050, 50, 1250, 50, 1350, 50 };

#define TONE_SEQ_SET_THRESH_LEN 4
uint16_t toneSeq_SetThreshold [TONE_SEQ_SET_THRESH_LEN] = { 1000, 100, 1500, 100 };

#define TONE_SEQ_CLR_THRESH_LEN 4
uint16_t toneSeq_ClearThreshold [TONE_SEQ_CLR_THRESH_LEN] = { 1500, 100, 1000, 100 };

#define TONE_SEQ_CLICK_LEN 2
uint16_t toneSeq_Click [TONE_SEQ_CLICK_LEN] = { 60, 10 };

#define TONE_SEQ_START_RACE_LEN 2
uint16_t toneSeq_StartRace [TONE_SEQ_START_RACE_LEN] = { 1500, 700 };

#define TONE_SEQ_END_RACE_LEN 10
uint16_t toneSeq_EndRace [TONE_SEQ_END_RACE_LEN] = { 1500, 120, 0, 30, 1500, 120, 0, 30, 1500, 120 };

#define TONE_SEQ_SETUP_THRESHOLD_START 10
uint16_t toneSeq_SetupThresholdStart [TONE_SEQ_SETUP_THRESHOLD_START] = { 659, 100, 0, 20, 659, 100, 0, 20, 880, 100 };

#define TONE_SEQ_SETUP_THRESHOLD_STOP 10
uint16_t toneSeq_SetupThresholdStop [TONE_SEQ_SETUP_THRESHOLD_STOP] = { 880, 100, 0, 20, 880, 100, 0, 20, 659, 100 };

#define TONE_SEQ_SETUP_THRESHOLD_MIDDLE 10
uint16_t toneSeq_SetupThresholdMiddle [TONE_SEQ_SETUP_THRESHOLD_MIDDLE] = { 659, 150, 0, 30, 784, 150, 0, 30, 880, 150 };

#define TONE_SEQ_SETUP_THRESHOLD_DONE 14
uint16_t toneSeq_SetupThresholdDone [TONE_SEQ_SETUP_THRESHOLD_DONE] = { 880, 150, 0, 30, 784, 150, 0, 30, 659, 150, 0, 30, 523, 500};


// ----------------------------------------------------------------------------
void startPlayingTones() {
    curToneIndex = 0;
    curDurIndex = 1;
    playSound = 1;
    playStartTime = 0;
}
// ----------------------------------------------------------------------------
void playLapTones() {
    curToneSeq = toneSeq_Detect;
    lastToneSeqIndex = TONE_SEQ_DETECT_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playSetThresholdTones() {
    curToneSeq = toneSeq_SetThreshold;
    lastToneSeqIndex = TONE_SEQ_SET_THRESH_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playClearThresholdTones() {
    curToneSeq = toneSeq_ClearThreshold;
    lastToneSeqIndex = TONE_SEQ_CLR_THRESH_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playClickTones() {
    curToneSeq = toneSeq_Click;
    lastToneSeqIndex = TONE_SEQ_CLICK_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playStartRaceTones() {
    curToneSeq = toneSeq_StartRace;
    lastToneSeqIndex = TONE_SEQ_START_RACE_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playEndRaceTones() {
    curToneSeq = toneSeq_EndRace;
    lastToneSeqIndex = TONE_SEQ_END_RACE_LEN - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playThresholdSetupStartTones() {
    curToneSeq = toneSeq_SetupThresholdStart;
    lastToneSeqIndex = TONE_SEQ_SETUP_THRESHOLD_START - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playThresholdSetupStopTones() {
    curToneSeq = toneSeq_SetupThresholdStop;
    lastToneSeqIndex = TONE_SEQ_SETUP_THRESHOLD_STOP - 1;
    startPlayingTones();
}// ----------------------------------------------------------------------------
void playThresholdSetupMiddleTones() {
    curToneSeq = toneSeq_SetupThresholdMiddle;
    lastToneSeqIndex = TONE_SEQ_SETUP_THRESHOLD_MIDDLE - 1;
    startPlayingTones();
}
// ----------------------------------------------------------------------------
void playThresholdSetupDoneTones() {
    curToneSeq = toneSeq_SetupThresholdDone;
    lastToneSeqIndex = TONE_SEQ_SETUP_THRESHOLD_DONE - 1;
    startPlayingTones();
}