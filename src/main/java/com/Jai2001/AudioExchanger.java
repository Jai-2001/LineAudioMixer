package com.Jai2001;

import javax.sound.sampled.Line;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.ConcurrentHashMap;

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
    }
    public void start(boolean background, boolean daemon){
        Runnable loop = new doExchange();
        if(background){
            Thread exchanger = new Thread(loop);
            exchanger.setDaemon(daemon);
            exchanger.start();
        }else {
            loop.run();
        }

    }
    public AudioDataStream getSyncedStream(TargetDataLine input) {
        Line.Info key = input.getLineInfo();
        AudioDataStream syncedStream = syncedStreams.get(key);
        if (syncedStream == null) {
            syncedStream = new AudioDataStream(input);
            syncedStreams.put(key, syncedStream);
            producerList = syncedStreams.values().toArray(new AudioDataStream[0]);
        }
        maxWait = Math.max(maxWait,(long) input.getFormat().getSampleRate());
        return syncedStream;
    }

    public boolean toggleSleep(boolean measure) {
        allowMeasure = measure;
        allowSleep = !allowSleep;
        return allowSleep;
    }

    private class doExchange implements Runnable {
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
}