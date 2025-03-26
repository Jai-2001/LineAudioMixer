package com.Jai2001.LineAudioMixer.Window;

import com.Jai2001.LineAudioMixer.AudioDataStream;
import com.Jai2001.LineAudioMixer.AudioExchanger;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class PipeElement {

    private HBox base;

    private TargetDataLine input;

    private SourceDataLine output;

    private ComboBox<Mixer> inputBox;

    private ComboBox<Mixer> outputBox;

    private Slider volumeSlider;

    private Button remover;

    private AudioDataStream stream;
    private AudioExchanger exchanger;


    public PipeElement(AudioExchanger sharedExchanger){
        exchanger = sharedExchanger;
        base = new HBox();
        outputBox = list(SourceDataLine.class, new pairSelector());
        inputBox = list(TargetDataLine.class, new pairSelector());
        base.getChildren().add(inputBox);
        base.getChildren().add(outputBox);
        volumeSlider = new Slider(0.5,1.5, 1);
        volumeSlider.setDisable(true);
        volumeSlider.addEventHandler(MouseEvent.MOUSE_RELEASED, new volumeAdjuster());
        base.getChildren().add(volumeSlider);
        ToggleButton swapperToggle = new ToggleButton("swap l/r");
        swapperToggle.addEventHandler(MouseEvent.MOUSE_CLICKED,new swapper());
        base.getChildren().add(swapperToggle);
        remover = new Button("-");
        remover.addEventHandler(MouseEvent.MOUSE_CLICKED, new pipeRemover());
        base.getChildren().add(remover);
    }

    public HBox getBase(){
        return base;
    }

    public double startPipe(AudioExchanger exchanger) {
        if (input == null || output == null) return -1;
        try {
            if(!exchanger.isStarted()){
                exchanger.start(true, true);
            }
            stream = exchanger.getSyncedStream(input);
            stream.addConsumer(output);
            stream.start();
            double volume = stream.getConsumerVolume(output);
            inputBox.setDisable(volume >= 0);
            outputBox.setDisable(volume >= 0);
            inputBox.getParent().getChildrenUnmodifiable().get(2).setDisable(volume < 0);
            return volume;
        } catch (Exception e) {
            e.printStackTrace();
            if (stream != null) stream.stop();
            Platform.runLater(() -> {
                inputBox.setValue(null);
                outputBox.setValue(null);
            });
            return -1;
        }
    }

    private class swapper implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
                try {
                    boolean swap = ((ToggleButton) e.getSource()).isSelected();
                    stream.setSwapped(output, swap);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
    };

     private class pairSelector implements EventHandler<ActionEvent> {
         @Override
         public void handle(ActionEvent e) {
             try {
                 Mixer inputMixer = inputBox.getValue();
                 Mixer outputMixer = outputBox.getValue();
                 if (inputMixer == null || outputMixer == null) return;
                 input = getLine(inputMixer, TargetDataLine.class);
                 output = getLine(outputMixer, SourceDataLine.class);
                 startPipe(exchanger);
             } catch (LineUnavailableException ex){
                 throw new RuntimeException(ex);
             }

         }
     };


    private class volumeAdjuster implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            try {
                double volume = volumeSlider.getValue();
                stream.setConsumerVolume(output, volume);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    private class muteBinder implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            try {
                double volume = volumeSlider.getValue();
                stream.setConsumerVolume(output, volume);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    };


    private class pipeRemover implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            try {
                stream.removeConsumer(output);
                VBox boxList = (VBox) base.getParent();
                boxList.getChildren().remove(base);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    };
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

    private static ComboBox<Mixer> list(Class<?> lineClass, EventHandler<ActionEvent> listener){
        ComboBox<Mixer> box = new ComboBox<>();
        box.setId(UUID.randomUUID().toString());
        box.setCellFactory((ListView<Mixer> view) -> new LineListCell());
        box.setOnAction(listener);
        box.setButtonCell(new LineListCell());
        Arrays.stream(AudioSystem.getMixerInfo())
                .map(AudioSystem::getMixer)
                .filter(info -> (info).isLineSupported(new Line.Info(lineClass)))
                .forEach(m -> box.getItems().addAll(m));
        box.getItems().add(null);
        return box;
    }

}
