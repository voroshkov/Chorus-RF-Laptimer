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

// rx5808 module needs >30ms to tune.
#define MIN_TUNE_TIME 30

void SERIAL_SENDBIT1() {
    digitalHigh(spiDataPin);
    delayMicroseconds(1);
    digitalHigh(spiClockPin);
    delayMicroseconds(1);
    digitalLow(spiClockPin);
    delayMicroseconds(1);
}

void SERIAL_SENDBIT0() {
    digitalLow(spiDataPin);
    delayMicroseconds(1);
    digitalHigh(spiClockPin);
    delayMicroseconds(1);
    digitalLow(spiClockPin);
    delayMicroseconds(1);
}

//default: high 00010000110000010011 low -> PD_IFAF | PD_DIV4 | PD_5GVCO | PD_REG1D8 | PD_DIV80 | PD_PLL1D8
#define PD_VCLAMP       0x00080000 /*Video clamp power down control */
#define PD_VAMP         0x00040000 /*Video amp power down control */
#define PD_IF_DEMOD     0x00020000 /*IF demodulator power down control */
#define PD_IFAF         0x00010000 /*IFAF power down control */
#define PD_RSSI_SQUELCH 0x00008000 /*RSSI & noise squelch power down control */
#define PD_REGBS        0x00004000 /*BS regulator power down control */
#define PD_REGIF        0x00002000 /*IF regulator power down control */
#define PD_BC           0x00001000 /*BC power down control */
#define PD_DIV4         0x00000800 /*Divide-by-4 power down control */
#define PD_5GVCO        0x00000400 /*5G VCO power down control */
#define PD_SYN          0x00000200 /*SYN power down control */
#define PD_AU6M         0x00000100 /*6M audio modulator power down control */
#define PD_6M           0x00000080 /*6M power down control */
#define PD_AU6M5        0x00000040 /*6M5 audio modulator power down control */
#define PD_6M5          0x00000020 /*6M5 power down control */
#define PD_REG1D8       0x00000010 /*1.8V regulator power down control */
#define PD_IFABF        0x00000008 /*IFABF power down control */
#define PD_MIXER        0x00000004 /*RF Mixer power down control */
#define PD_DIV80        0x00000002 /*Divide-by-80 power down control */
#define PD_PLL1D8       0x00000001 /*PLL 1.8V regulator power down control */

//Power Down Control Register
void PowerDownFeatures(uint32_t features)
{
    uint8_t i;
    digitalLow(slaveSelectPin);
    delayMicroseconds(1);
    
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT1();

    SERIAL_SENDBIT1();

    for (i = 20; i > 0; i--) {
        // Is bit high or low?
        if (features & 0x1) {
            SERIAL_SENDBIT1();
        }
        else {
            SERIAL_SENDBIT0();
        }
        features >>= 1;
    }

    digitalHigh(slaveSelectPin);
    delayMicroseconds(1);
    delay(MIN_TUNE_TIME);
}

void setupSPIpins() {
    // SPI pins for RX control
    pinMode (slaveSelectPin, OUTPUT);
    pinMode (spiDataPin, OUTPUT);
    pinMode (spiClockPin, OUTPUT);

    digitalLow(spiClockPin);
    digitalHigh(slaveSelectPin);
    delayMicroseconds(1);
    
    PowerDownFeatures(PD_IFAF | PD_DIV4 | PD_5GVCO | PD_REG1D8 | PD_DIV80 | PD_PLL1D8 | PD_IF_DEMOD | PD_VAMP | PD_VCLAMP | PD_MIXER | PD_IFABF | PD_6M5 | PD_AU6M5 | PD_6M | PD_AU6M | PD_SYN | PD_REGIF);
}

void powerDownModule() {
    cli();
    SERIAL_ENABLE_HIGH();
    SERIAL_ENABLE_LOW();

    // send 0x0A
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT1();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT1();

    // write
    SERIAL_SENDBIT1();

    // set all bits to one -> disable all modules
     for (uint8_t i = 20; i > 0; i--) {
        SERIAL_SENDBIT1();
    }
    // Finished clocking data in
    SERIAL_ENABLE_HIGH();
    delayMicroseconds(1);

    digitalLow(slaveSelectPin);
    digitalLow(spiClockPin);
    digitalLow(spiDataPin);
    sei();

    delay(MIN_TUNE_TIME);
}

void resetModule() {
    cli();
    SERIAL_ENABLE_HIGH();
    SERIAL_ENABLE_LOW();

    // State register
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();

    // write
    SERIAL_SENDBIT1();

    // set all bits to zero -> reset
    for (int i = 20; i > 0; i--) {
        SERIAL_SENDBIT0();
    }

    // Finished clocking data in
    SERIAL_ENABLE_HIGH();
    delayMicroseconds(1);

    digitalLow(slaveSelectPin);
    digitalLow(spiClockPin);
    digitalLow(spiDataPin);
    sei();

    delay(MIN_TUNE_TIME);
}

uint16_t setModuleFrequency(uint16_t frequency) {
    uint8_t i;
    uint16_t channelData;

    channelData = frequency - 479;
    channelData /= 2;
    i = channelData % 32;
    channelData /= 32;
    channelData = (channelData << 7) + i;

    // Second is the channel data from the lookup table
    // 20 bytes of register data are sent, but the MSB 4 bits are zeros
    // register address = 0x1, write, data0-15=channelData data15-19=0x0
    digitalLow(spiClockPin);
    digitalLow(slaveSelectPin);
    delayMicroseconds(1);

    // Register 0x1
    SERIAL_SENDBIT1();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();

    // Write to register
    SERIAL_SENDBIT1();

    // D0-D15
    //   note: loop runs backwards as more efficent on AVR
    for (i = 16; i > 0; i--) {
        // Is bit high or low?
        if (channelData & 0x1) {
            SERIAL_SENDBIT1();
        }
        else {
            SERIAL_SENDBIT0();
        }
        // Shift bits along to check the next one
        channelData >>= 1;
    }

    // Remaining D16-D19
    for (i = 4; i > 0; i--) {
        SERIAL_SENDBIT0();
    }

    // Finished clocking data in
    digitalHigh(slaveSelectPin);
    delayMicroseconds(1);
    
    delay(MIN_TUNE_TIME);
    
    return frequency;
}

uint16_t setModuleChannel(uint8_t channel, uint8_t band) {
    uint16_t frequency = pgm_read_word_near(channelFreqTable + channel + (8 * band));
    return setModuleFrequency(frequency);
}
