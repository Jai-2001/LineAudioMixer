package com.Jai2001;

import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.HashMap;

/**
 * AudioDataStream represents an audio stream with a {@link TargetDataLine} input
 * and multiple {@link SourceDataLine} outputs.
 * <p>
 * It contains a map of {@link AudioConsumer}s for each output, along with arrays
 * to store the map entries.
 * <p>
 * The buffer holds audio data read from the input before sending to outputs.
 * <p>
 * Consumers can be added, removed, and have their volume adjusted.
 * <p>
 * The stream can be started to begin reading data from the input and sending
 * it to the consumer outputs. It can also be stopped to close the lines.
 */
public class AudioDataStream {

    /**A {@link HashMap} mapping {@link Line.Info} to {@link AudioConsumer} for each audio output.*/
    protected final HashMap<Line.Info, AudioConsumer> consumers;

    /**Array of {@link AudioConsumer} entries from the map.*/
    protected AudioConsumer[] consumersList;

    /**The {@link TargetDataLine} for audio input.*/
    protected final TargetDataLine input;

    /**Byte array to hold audio data read from input.*/
    protected final byte[] buffer;

    /**Flag indicating if the stream is running.*/
    protected boolean started;

    /**
     * Constructs an AudioDataStream with the given input TargetDataLine.
     *
     * @param input The TargetDataLine to read audio data from.
     */
    public AudioDataStream(TargetDataLine input) {
        this.input = input;
        this.buffer = new byte[input.getBufferSize()];
        consumers = new HashMap<>();
        consumersList = new AudioConsumer[0];
        started = false;
    }

    /**
     * Adds an audio output consumer for the given SourceDataLine.
     * <p>
     * Opens, starts, and creates an {@link AudioConsumer} for the output line.
     * Adds it to the consumers map and list.
     *
     * @param output The SourceDataLine to add as an output.
     * @throws LineUnavailableException If the line cannot be opened.
     */
    public void addConsumer(SourceDataLine output) throws LineUnavailableException {
        if(!output.isOpen()) output.open();
        output.start();
        AudioConsumer consumer = new AudioConsumer(output, scaleToDecibels(1), buffer);
        consumers.putIfAbsent(output.getLineInfo(),consumer);
        consumersList = consumers.values().toArray(new AudioConsumer[0]);
    }

    /**
     * Scales a linear volume value to decibels using an exponential scale.
     * <p>
     * The formula used is:
     * <I>decibels = 0.001e<SUP>(6.908*linear)</SUP></I>
     * <p>
     * This scales the linear volume range, for example from 0.1 to 1.5 to an exponential
     * decibel range of -100 to ~31.63 dB.
     * <p>
     * Values below 0.1 linear are clipped to -100 dB. This avoids taking the
     * log of zero which would result in NaN.
     *
     * @param linear The linear volume value to scale upwards of 0.1.
     * @return The volume in decibels upwards of -100dB.
     */
    public static double scaleToDecibels(double linear){
        return linear < 0.1 ? 0: 0.001 * Math.exp(6.908 * linear);
    }

    /**
     * Scales a decibel value back to a linear volume.
     * <p>
     * This is the inverse of {@link #scaleToDecibels(double)}.
     *
     * @param exponential The volume in decibels to scale.
     * @return The equivalent linear volume value.
     */
    public static double scaleFromDecibels(double exponential){
        return Math.log(exponential* 1000)/6.908;
    }

    /**
     * Sets the volume for the given output consumer.
     *
     * @param output The {@link SourceDataLine} of the consumer to adjust.
     * @param volume The desired linear volume level to set.
     * <p>
     * If the output exists in the consumers map, this scales the given volume to
     * decibels using {@link #scaleToDecibels(double)} and sets it on the
     * {@link AudioConsumer}.
     */
    public void setConsumerVolume(SourceDataLine output, double volume){
        if(consumers.containsKey(output.getLineInfo())){
            consumers.get(output.getLineInfo()).volume = scaleToDecibels(volume);;
        }
    }

    /**
     * Gets the linear volume level for the given output consumer.
     *
     * @param output The {@link SourceDataLine} of the consumer to get volume for.
     * @return The linear volume level, or -1 if consumer not found.
     * <p>
     * If the output exists in the consumers map, this gets the {@link AudioConsumer}
     * and returns the inverse scaling of its volume level to linear using
     * {@link #scaleFromDecibels(double)}.
     */
    public double getConsumerVolume(SourceDataLine output){
        if(consumers.containsKey(output.getLineInfo())){
            return scaleFromDecibels(consumers.get(output.getLineInfo()).volume);
        }
        return -1;
    }

    /**
     * Removes the given output consumer from this stream.
     *
     * @param output The {@link SourceDataLine} of the consumer to remove.
     * <p>
     * This flushes and stops the output line, then removes it
     * from the consumers map and list.
     */
    public void removeConsumer(SourceDataLine output){
        output.flush();
        output.stop();
        consumers.remove(output.getLineInfo());
        consumersList = consumers.values().toArray(new AudioConsumer[0]);
    }

    /**
     * Starts the audio stream if it is not already running.
     * <p>
     * Opens and starts the input {@link TargetDataLine} if needed.
     * Sets the started flag to true.
     *
     * @throws LineUnavailableException If the input line cannot be opened.
     */
    public void start() throws LineUnavailableException {
        if(!started){
            if(!input.isOpen()) input.open();
            input.start();
            started = true;
        }

    }

    /**
     * Stops the audio stream and closes the lines.
     * <p>
     * Loops through and drains each {@link AudioConsumer} output.
     * Clears the consumers map and list.
     * <p>
     * Stops and closes the input {@link TargetDataLine} if active.
     */
    public void stop(){
        started = false;
        for (AudioConsumer output : consumers.values()) {
            output.line.drain();
        }
        consumers.clear();
        if(input.isActive())input.stop();
        input.close();
    }

}
