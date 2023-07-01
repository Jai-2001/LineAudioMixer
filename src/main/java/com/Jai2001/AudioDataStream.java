package com.Jai2001;

import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.HashMap;

public class AudioDataStream {

    protected final HashMap<Line.Info, AudioConsumer> consumers;
    protected AudioConsumer[] consumersList;
    protected final TargetDataLine input;
    protected final byte[] buffer;
    protected boolean started;

    public AudioDataStream(TargetDataLine input) {
        this.input = input;
        this.buffer = new byte[input.getBufferSize()];
        consumers = new HashMap<>();
        consumersList = new AudioConsumer[0];
        started = false;
    }

    public void addConsumer(SourceDataLine output) throws LineUnavailableException {
        if(!output.isOpen()) output.open();
        output.start();
        AudioConsumer consumer = new AudioConsumer(output, scaleToDecibels(1), buffer);
        consumers.putIfAbsent(output.getLineInfo(),consumer);
        consumersList = consumers.values().toArray(new AudioConsumer[0]);
    }

    public static double scaleToDecibels(double linear){
        return linear < 0.1 ? 0: 0.001 * Math.exp(6.908 * linear);
    }

    public static double scaleFromDecibels(double exponential){
        return Math.log(exponential* 1000)/6.908;
    }

    public void setConsumerVolume(SourceDataLine output, double volume){
        if(consumers.containsKey(output.getLineInfo())){
            consumers.get(output.getLineInfo()).volume = scaleToDecibels(volume);;
        }
    }

    public double getConsumerVolume(SourceDataLine output){
        if(consumers.containsKey(output.getLineInfo())){
            return scaleFromDecibels(consumers.get(output.getLineInfo()).volume);
        }
        return -1;
    }

    public void removeConsumer(SourceDataLine output){
        output.flush();
        output.stop();
        consumers.remove(output.getLineInfo());
        consumersList = consumers.values().toArray(new AudioConsumer[0]);
    }

    public void start() throws LineUnavailableException {
        if(!started){
            if(!input.isOpen()) input.open();
            input.start();
            started = true;
        }

    }

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
