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

#define MAX_BAND 7

// Channels to send to the SPI registers
const uint16_t channelTable[] PROGMEM = {
    // Channel 1 - 8
    0x281D, 0x288F, 0x2902, 0x2914, 0x2987, 0x2999, 0x2A0C, 0x2A1E, // Raceband
    0x2A05, 0x299B, 0x2991, 0x2987, 0x291D, 0x2913, 0x2909, 0x289F, // Band A
    0x2903, 0x290C, 0x2916, 0x291F, 0x2989, 0x2992, 0x299C, 0x2A05, // Band B
    0x2895, 0x288B, 0x2881, 0x2817, 0x2A0F, 0x2A19, 0x2A83, 0x2A8D, // Band E
    0x2906, 0x2910, 0x291A, 0x2984, 0x298E, 0x2998, 0x2A02, 0x2A0C, // Band F / Airwave
    0x2609, 0x261C, 0x268E, 0x2701, 0x2713, 0x2786, 0x2798, 0x280B, // Band D / 5.3
    0x248f, 0x2499, 0x2503, 0x250d, 0x2909, 0x2913, 0x291d, 0x2987, //connex
    0x2991, 0x299b, 0x299b, 0x299b, 0x299b, 0x299b, 0x299b, 0x299b  //even more connex, last 6 unused!!!    
};

// Channels' MHz Values. Just for reference. Not used in code.
//
 const uint16_t channelFreqTable[] PROGMEM = {
//     // Channel 1 - 8
     5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917, // Raceband
     5865, 5845, 5825, 5805, 5785, 5765, 5745, 5725, // Band A
     5733, 5752, 5771, 5790, 5809, 5828, 5847, 5866, // Band B
     5705, 5685, 5665, 5645, 5885, 5905, 5925, 5945, // Band E
     5740, 5760, 5780, 5800, 5820, 5840, 5860, 5880, // Band F / Airwave
     5362, 5399, 5436, 5473, 5510, 5547, 5584, 5621, // Band D / 5.3
     5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805, // connex
     5825, 5845, 5845, 5845, 5845, 5845, 5845, 5845  // even more connex, last 6 unused!!!
 };
