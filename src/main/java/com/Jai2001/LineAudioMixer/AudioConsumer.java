package com.Jai2001.LineAudioMixer;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import javax.sound.sampled.SourceDataLine;

/**
 * AudioConsumer represents an audio output stream with volume control.
 * <p>
 * It contains a SourceDataLine for audio output, along with a volume adjusted buffer
 * and the original byte array for the audio data.
 * <p>
 * The volume property controls the volume scaling applied to the samples
 * before writing them to the output line.
 * <p>
 * The adjustVolume() method reads samples from the original byte array,
 * combines the stereo samples, scales them by the volume, and writes them
 * to the buffer.
 * <p>
 * This buffer can then be written to the SourceDataLine for playback.
 */
public class AudioConsumer {
    public SourceDataLine line;

    /**The volume scaling factor applied to samples.*/
    public double volume;

    /**Byte array containing the volume adjusted samples.*/
    public byte[] buffer;

    /**The original byte array containing the raw audio data.*/
    public final byte[] original;

    public int muteBind;

    public boolean muted;

    public boolean swap = false;

    public short swapBuffer = 0;

    /**
     * Constructs an AudioConsumer.
     *
     * @param line The SourceDataLine for audio output.
     * @param volume The initial volume scaling factor.
     * @param original A byte array that should always contain the raw audio data pre-adjustment.
     */
    public AudioConsumer(SourceDataLine line, double volume, byte[] original){
        this.line = line;
        this.volume = volume;
        this.original = original;
        this.buffer = new byte[original.length];
        this.muted = false;
        this.muteBind = NativeKeyEvent.VC_UNDEFINED;
    }

    /**
     * Adjusts the volume of audio samples and writes them to the buffer.
     * <p>
     * This iterates through the provided number of samples from the original
     * byte array. It combines the interleaved stereo samples into a single
     * short value, scales that value by the volume property, then writes the
     * adjusted samples to the buffer field.
     *
     * @param samples The number of samples to process.
     */
    public void adjustVolume(int samples) {
        if(samples!=0) {
            swapBuffer = original[0];
            for (int i = 0; i < samples; i+=2) {
                short right = original[i+1];
                short left= original[i];
                right = (short) ((right & 0xff) << 8);
                left = (short) (left & 0xff);
                short combined = (short) (right | left) ;
                combined = (short) (combined * volume);
                if(swap){
                    short next = combined;
                    combined = swapBuffer;
                    swapBuffer = next;
                }
                buffer[i] = (byte) combined;
                buffer[i+1] = (byte) (combined >> 8);

            }
        }


    }
}
