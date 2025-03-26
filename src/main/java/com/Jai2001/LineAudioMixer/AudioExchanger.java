package com.Jai2001.LineAudioMixer;

import javax.sound.sampled.Line;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AudioExchanger is used to synchronize multiple audio streams.
 * <p>
 * It contains a map of {@link AudioDataStream}s keyed by the {@link Line.Info}
 * of their {@link TargetDataLine}.
 * <p>
 * When a new TargetDataLine is passed to {@code getSyncedStream()}, if no existing
 * AudioDataStream exists for it, a new one is created and added to the map. All the
 * streams are stored in the {@code producerList} array.
 * <p>
 * The main loop iterates through each producer AudioDataStream, reads available data
 * from the TargetDataLine into a buffer, then writes that buffer to each subscribed
 * consumer.
 * <p>
 * Sleeping can be toggled on/off. When on, the thread sleeps based on the time since
 * the last loop iteration and estimated time per loop iteration.
 * <p>
 * Statistics like loop duration, sleep time, etc. can also be measured when enabled.
 */
public class AudioExchanger {
    final ConcurrentHashMap<Line.Info, AudioDataStream> syncedStreams;
    static AudioDataStream[] producerList;
    boolean allowSleep;
    boolean allowMeasure;
    long timeSinceLast;
    long loopStart;
    long transferTime;
    long pollStart;
    public long maxWait;
    private final StringBuilder infoBuilder;

    private boolean started;

    /**
     * Constructs an AudioExchanger.
     *
     * @param sleep Whether to allow the main loop to sleep between iterations to
     * reduce CPU usage. Sleeping can be toggled via {@link #toggleSleep(boolean)}.
     */
    public AudioExchanger(boolean sleep) {
        syncedStreams = new ConcurrentHashMap<>();
        producerList = new AudioDataStream[0];
        allowSleep = sleep;
        allowMeasure = sleep;
        timeSinceLast = 0;
        loopStart = 0;
        transferTime = 0;
        pollStart = 0;
        maxWait = 0;
        infoBuilder = new StringBuilder();
        started = false;
    }

    /**
     * Starts the main exchange loop in a background thread or the current thread.
     *
     * @param background Whether to run the loop in a background thread. If false,
     * runs in the current thread.
     * @param daemon If running in a background thread, whether to set it as a daemon
     * thread that does not prevent the JVM from exiting.
     */
    public void start(boolean background, boolean daemon){
        started = true;
        Runnable loop = new doExchange();
        if(background){
            Thread exchanger = new Thread(loop);
            exchanger.setDaemon(daemon);
            exchanger.start();
        }else {
            loop.run();
        }

    }

    /**
     * Gets an {@link AudioDataStream} that is synchronized with the other streams.
     * <p>
     * If no existing AudioDataStream exists for the given {@link TargetDataLine}, a new one
     * is created and added to the {@code syncedStreams} map.
     *
     * @param input The TargetDataLine to synchronize.
     * @return The AudioDataStream for the given input TargetDataLine.
     */
    public AudioDataStream getSyncedStream(TargetDataLine input) {
        Line.Info key = input.getLineInfo();
        AudioDataStream syncedStream = syncedStreams.get(key);
        if (syncedStream == null) {
            syncedStream = new AudioDataStream(input);
            syncedStreams.put(key, syncedStream);
            producerList = syncedStreams.values().toArray(new AudioDataStream[0]);
        }
        maxWait = Math.max(maxWait,(long) 1e9 / (long) input.getFormat().getSampleRate());
        return syncedStream;
    }

    /**
     * Toggles whether the main loop is allowed to sleep between iterations.
     *
     * @param measure Whether to collect timing statistics. Statistics will only be
     * collected if sleeping is enabled.
     * @return The new value of allowSleep after toggling.
     */
    public boolean toggleSleep(boolean measure) {
        allowMeasure = measure;
        allowSleep = !allowSleep;
        return allowSleep;
    }

    /**
     * Inner class that runs the main exchange loop.
     */
    private class doExchange implements Runnable {
        /**
         * Runs the main loop that exchanges audio data between the synchronized streams.
         * <p>
         * The loop iterates through each producer AudioDataStream and reads available data into
         * a buffer. It then writes the buffer to each subscribed consumer AudioDataStream.
         * <p>
         * Sleeping and measurement can be enabled to reduce CPU usage and collect timing stats.
         */
        public void run(){
            int bytesRead;
            AudioDataStream stream;
            AudioConsumer output;
            while (true){
                if(allowSleep){
                    int delay = (int) (timeSinceLast - transferTime);
                    delay = delay < 0 || delay > maxWait * producerList.length ? 0 : delay;
                    try {
                        Thread.sleep(0L, delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                loopStart = System.nanoTime();
                transferTime = 0;
                for (int j = 0, producerLength = AudioExchanger.producerList.length; j < producerLength; j++) {
                    stream = AudioExchanger.producerList[j];
                    if(stream.started){
                        if (stream.consumers.isEmpty()) {
                            stream.stop();
                            syncedStreams.remove(stream.input.getLineInfo());
                        } else {
                            if(allowMeasure) pollStart = System.nanoTime();
                            bytesRead = stream.input.available();
                            if ((bytesRead = stream.input.read(stream.buffer, 0, bytesRead)) != -1) {
                                for (int i = 0, consumersListLength = stream.consumersList.length; i < consumersListLength; i++) {
                                    output = stream.consumersList[i];
                                    output.adjustVolume(bytesRead);
                                    output.line.write(output.buffer, 0, bytesRead);
                                }
                                if(allowMeasure)
                                    transferTime += System.nanoTime() - pollStart;
                            }
                        }
                    }
                }
                if(allowMeasure) timeSinceLast = System.nanoTime() - loopStart;
            }
        }

    }

    /**
     * Gets formatted information about the audio exchange.
     *
     * @return A string containing information like:
     * <ul>
     *     <li>Average loop duration</li>
     *     <li>Average sleep time</li>
     *     <li>Total audio transfer time</li>
     *     <li>Other stats if enabled</li>
     * </ul>
     */
    public String getExtraInfo(){
        infoBuilder.setLength(0);
        infoBuilder.append("Max wait: ");
        infoBuilder.append(maxWait);
        infoBuilder.append(", Iteration Time: ");
        infoBuilder.append(timeSinceLast);
        infoBuilder.append(", Transfer Time: ");
        infoBuilder.append(transferTime);
        return infoBuilder.toString();
    }

    public boolean isStarted(){
        return started;
    }
}