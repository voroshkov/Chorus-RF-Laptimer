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

//--------------------------------------------------------

#define TO_BYTE(i) (i <= '9' ? i - 0x30 : i - 0x41 + 10)

uint8_t HEX_TO_BYTE (uint8_t hi, uint8_t lo) {
    return TO_BYTE(hi)*16+TO_BYTE(lo);
}

uint16_t HEX_TO_UINT16 (uint8_t * buf) {
    return (HEX_TO_BYTE(buf[0], buf[1]) << 8) + (HEX_TO_BYTE(buf[2], buf[3]));
}

int32_t HEX_TO_SIGNED_LONG (uint8_t * buf) {
    #define LEN 8
    int32_t temp = 0;
    for (int i = 0; i < LEN; i++) {
        temp += TO_BYTE(buf[LEN-1-i])*(int32_t)1<<(i*4);
    }
    return temp;
}

//--------------------------------------------------------

#define TO_HEX(i) (i <= 9 ? 0x30 + i : 0x41 + i - 10)

void halfByteToHex(uint8_t *buf, uint8_t val) {
    buf[0] = TO_HEX((val & 0x0F));
}

void byteToHex(uint8_t *buf, uint8_t val) {
    halfByteToHex(buf, val >> 4);
    halfByteToHex(&buf[1], val);
}

void intToHex(uint8_t *buf, uint16_t val) {
    byteToHex(buf, val >> 8);
    byteToHex(&buf[2], val & 0x00FF);
}

void longToHex(uint8_t *buf, uint32_t val) {
    intToHex(buf, val >> 16);
    intToHex(&buf[4], val & 0x0000FFFF);
}

void writeSendHeader() {
    Serial.write("S");
    Serial.write(MODULE_ID_HEX);
}

uint8_t send4BitsToSerial(uint8_t prefix, uint8_t byteValue) {
    if (Serial.availableForWrite() >= 5) { // e.g. "S1R1\n"
        writeSendHeader();
        Serial.write(prefix);
        uint8_t buf;
        halfByteToHex(&buf, byteValue);
        Serial.write(buf);
        Serial.write(SERIAL_DATA_DELIMITER);
        return(1);
    }
    return(0);
}

uint8_t sendByteToSerial(uint8_t prefix, uint8_t byteValue) {
    if (Serial.availableForWrite() >= 6) {
        writeSendHeader();
        Serial.write(prefix);
        uint8_t buf[2];
        byteToHex(buf, byteValue);
        Serial.write(buf, 2);
        Serial.write(SERIAL_DATA_DELIMITER);
        return(1);
    }
    return(0);
}

uint8_t sendIntToSerial(uint8_t prefix, uint16_t intValue) {
    if (Serial.availableForWrite() >= 8) {
        writeSendHeader();
        Serial.write(prefix);
        uint8_t buf[4];
        intToHex(buf, intValue);
        Serial.write(buf, 4);
        Serial.write(SERIAL_DATA_DELIMITER);
        return(1);
    }
    return(0);
}

uint8_t sendLongToSerial(uint8_t prefix, uint32_t longValue) {
    if (Serial.availableForWrite() >= 12) {
        writeSendHeader();
        Serial.write(prefix);
        uint8_t buf[8];
        longToHex(buf, longValue);
        Serial.write(buf, 8);
        Serial.write(SERIAL_DATA_DELIMITER);
        return(1);
    }
    return(0);
}

uint8_t sendLaptimeToSerial(uint8_t prefix, uint8_t counter, uint32_t longValue) {
    if (Serial.availableForWrite() >= 14) {
        writeSendHeader();
        Serial.write(prefix);
        uint8_t buf[8];
        byteToHex(buf, counter);
        Serial.write(buf, 2);
        longToHex(buf, longValue);
        Serial.write(buf, 8);
        Serial.write(SERIAL_DATA_DELIMITER);
        return(1);
    }
    return(0);
}

