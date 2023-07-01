package com.Jai2001;

import javax.sound.sampled.SourceDataLine;

public class AudioConsumer {
    public SourceDataLine line;
    public double volume;
    public byte[] buffer;
    public final byte[] original;

    public AudioConsumer(SourceDataLine line, double volume, byte[] original){
        this.line = line;
        this.volume = volume;
        this.original = original;
        this.buffer = new byte[original.length];
    }

    public void adjustVolume(int limit) {
        for (int i = 0; i < limit; i+=2) {
            short buf1 = original[i+1];
            short buf2 = original[i];
            buf1 = (short) ((buf1 & 0xff) << 8);
            buf2 = (short) (buf2 & 0xff);
            short res= (short) (buf1 | buf2);
            res = (short) (res * volume);
            buffer[i] = (byte) res;
            buffer[i+1] = (byte) (res >> 8);
        }
    }
}
