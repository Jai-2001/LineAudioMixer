package com.Jai2001.LineAudioMixer.Window;

import javafx.scene.control.ListCell;

import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

public class LineListCell extends ListCell<Mixer> {

    static boolean extraInfo = false;

    @Override
    protected void updateItem(Mixer item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setText(null);
        } else {
            String lineName = item.getMixerInfo().getName();
            if(extraInfo){
                SourceDataLine asSource = getLineOrNull(item, SourceDataLine.class);
                if(asSource!=null) lineName += " [" + asSource.getBufferSize() + " / " + asSource.getFormat().getSampleRate() + "]";
                TargetDataLine asTarget = getLineOrNull(item, TargetDataLine.class);
                if(asTarget!=null) lineName += " [" + asTarget.getBufferSize() + " / " + asTarget.getFormat().getSampleRate() + "]";
            }
            setText(lineName);
        }
    }

    private static <T extends DataLine> T getLine(Mixer device, Class<T> targetClass) throws LineUnavailableException {
        device.open();
        Line.Info[] inputs = device.getSourceLineInfo();
        Line.Info[] outputs = device.getTargetLineInfo();
        Stream<Line.Info> both = Stream.concat(Arrays.stream(inputs),Arrays.stream(outputs));
        both = both.filter(o-> o.getLineClass() == targetClass);
        Optional<Line.Info> potentialMatch = both.findFirst();
        if(potentialMatch.isEmpty()) throw new LineUnavailableException();
        T line = (T) device.getLine(potentialMatch.get());
        return line;
    }


    private static <T extends DataLine> T getLineOrNull(Mixer device, Class<T> targetClass){
        try {
            return getLine(device, targetClass);
        } catch (LineUnavailableException e) {
            return null;
        }
    }
}