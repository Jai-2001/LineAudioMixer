package com.Jai2001.LineAudioMixer.Window;

import com.Jai2001.LineAudioMixer.AudioExchanger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.CacheHint;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.Objects;

public class MixerWindow extends Application {

    public static void main(String[] args) {
        Application.launch(MixerWindow.class);
    }
    static VBox boxList = new VBox();
    static Stage pStage;

    static Text bottomText = new Text();
    static AudioExchanger exchanger = new AudioExchanger(true);



    static boolean extraInfo = false;
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

    private static void addConnectors(){
        PipeElement connector = new PipeElement(exchanger);
        HBox listing = connector.getBase();
        boxList.getChildren().add(listing);
        pStage.sizeToScene();
    }



}