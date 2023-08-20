package com.Jai2001;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class MixerWindow extends Application {

    public static void main(String[] args) {
        Application.launch(MixerWindow.class);
    }
    static VBox boxList = new VBox();
    static Stage pStage;
    static HashMap<Parent, AudioDataStream> channels = new HashMap<>();
    static boolean extraInfo = false;
    static Text bottomText = new Text();
    static AudioExchanger exchanger = new AudioExchanger(true);


    @Override
    public void start(Stage stage) {
        pStage = stage;
        try{
            Image icon = new Image(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("icon.png")));
            stage.getIcons().add(icon);
        } catch (Throwable t){};
        stage.setTitle("Line Audio Mixer");
        System.out.println(Platform.isSupported(ConditionalFeature.SCENE3D));
        stage.setOnCloseRequest(t -> {
            Platform.exit();
            System.exit(0);
        });
        Button adder = new Button("+");
        adder.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> addConnectors());
        GridPane gridPane = new GridPane();
        gridPane.addRow(0, boxList);
        gridPane.addRow(1, adder);
        gridPane.addRow(2, bottomText);
        addConnectors();
        MenuItem toggleSleepItem = new MenuItem("Disable sleep");
        toggleSleepItem.setOnAction(toggleSleep);
        MenuItem enableExtraInfo = new MenuItem("Enable extra info");
        enableExtraInfo.setOnAction(toggleExtraInfo);
        ContextMenu debug = new ContextMenu(toggleSleepItem, enableExtraInfo);
        stage.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e->debug.show(stage, e.getScreenX(), e.getScreenY()));
        gridPane.setCache(false);
        gridPane.setCacheHint(CacheHint.SPEED);
        stage.setScene(new Scene(gridPane));
        stage.show();
    }

    static EventHandler<ActionEvent> toggleSleep =
            (e) -> ((MenuItem) e.getSource()).setText(exchanger.toggleSleep(extraInfo) ? "Disable sleep" : "Enable sleep");

    static final AnimationTimer debugTimings = new AnimationTimer() {
        @Override
        public void handle(long l) {
            if(l%60==0){
                bottomText.setText(exchanger.getExtraInfo());
            }
        }
    };
    static EventHandler<ActionEvent> toggleExtraInfo = (e) -> {
        extraInfo = !extraInfo;
        if (extraInfo) {
            ((MenuItem) e.getSource()).setText("Disable extra info");
            debugTimings.start();
        } else {
            ((MenuItem) e.getSource()).setText("Enable extra info");
            debugTimings.stop();
            bottomText.setText("");
        }
    };
    static EventHandler<MouseEvent> adjustVolume = (e) -> {
        try {
            Slider source = (Slider) e.getSource();
            double volume = source.getValue();
            SourceDataLine outputLine = getLine(getMixersFromCombo(source)[1].getValue(), SourceDataLine.class);
            channels.get(source.getParent()).setConsumerVolume(outputLine, volume);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    };

    static EventHandler<ActionEvent> selectPair = (e) -> {
        ComboBox<Mixer>[] pair = getMixersFromCombo((Node) e.getSource());
        startPipe(pair[0], pair[1]);
    };
    static EventHandler<MouseEvent> removeConnector = (e) -> {
        try {
            AudioDataStream toRemove = channels.get(((Node) e.getSource()).getParent());
            Mixer outBox = getMixersFromCombo((Node) e.getSource())[1].getValue();
            if(outBox!=null){
                toRemove.removeConsumer(getLine(outBox, SourceDataLine.class));
            }
            boxList.getChildren().remove(((Node) e.getSource()).getParent());
            channels.remove(((Node) e.getSource()).getParent());
        } catch (LineUnavailableException ex) {
            throw new RuntimeException(ex);
        }
    };

    private static void addConnectors(){
        HBox connector = new HBox();
        ComboBox<Mixer> speaker = list(SourceDataLine.class, selectPair);
        connector.getChildren().add(list(TargetDataLine.class, selectPair));
        connector.getChildren().add(speaker);
        Slider volumeSet = new Slider(0.5,1.5, 1);
        volumeSet.setDisable(true);
        volumeSet.addEventHandler(MouseEvent.MOUSE_RELEASED, adjustVolume);
        connector.getChildren().add(volumeSet);
        Button remover = new Button("-");
        remover.addEventHandler(MouseEvent.MOUSE_CLICKED, removeConnector);
        connector.getChildren().add(remover);
        boxList.getChildren().add(connector);
        pStage.sizeToScene();
    }

    private static double startPipe(ComboBox<Mixer> inputBox, ComboBox<Mixer> outputBox){
        Mixer input = inputBox.getValue();
        Mixer output = outputBox.getValue();
        if(input==null || output == null) return -1;
        AudioDataStream audioExchanger = null;
        if(!exchanger.isStarted()){
            exchanger.start(true, true);
        }
        try {
            TargetDataLine inputLine = getLine(input,TargetDataLine.class);
            audioExchanger = exchanger.getSyncedStream(inputLine);
            SourceDataLine outputLine = getLine(output, SourceDataLine.class);
            audioExchanger.addConsumer(outputLine);
            audioExchanger.start();
            channels.putIfAbsent(inputBox.getParent(), audioExchanger);
            double volume = audioExchanger.getConsumerVolume(outputLine);
            inputBox.setDisable(volume>=0);
            outputBox.setDisable(volume>=0);
            inputBox.getParent().getChildrenUnmodifiable().get(2).setDisable(volume < 0);
            return volume;
        } catch (Exception e) {
            e.printStackTrace();
            if(audioExchanger!=null) audioExchanger.stop();
            Platform.runLater(()->{
                inputBox.setValue(null);
                outputBox.setValue(null);
            });
            return -1;
        }
    }

    private static ComboBox<Mixer>[] getMixersFromCombo(Node combo){
        ObservableList<Node> mixerNodes = combo.getParent().getChildrenUnmodifiable();
        ComboBox<?>[] mixerPair = new ComboBox[2];
        mixerPair[0] = (ComboBox<?>) mixerNodes.get(0);
        mixerPair[1] = (ComboBox<?>) mixerNodes.get(1);
        return (ComboBox<Mixer>[]) mixerPair;
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

    private static <T extends DataLine> T getLineOrNull(Mixer device, Class<T> targetClass){
        try {
            return getLine(device, targetClass);
        } catch (LineUnavailableException e) {
            return null;
        }
    }
    private static class LineListCell extends ListCell<Mixer> {
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
    }

}