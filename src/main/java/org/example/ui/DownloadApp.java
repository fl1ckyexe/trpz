package org.example.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;

import org.example.core.AppSettings;
import org.example.core.DownloadManager;
import org.example.downloader.HttpDownloader;
import org.example.model.DownloadTask;
import org.example.observer.DownloadObserver;
import org.example.segment.SegmentManager;
import org.example.speed.SpeedControl;
import org.example.storage.LocalStorage;
import org.example.storage.SQLiteStorage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DownloadApp extends Application {

    private static final String COLOR_ASH = "#323030";
    private static final String COLOR_OAT = "#CDBEA7";

    private DownloadManager manager;
    private AppSettings settings;

    private TextArea logArea;
    private TextField urlField;

    // ===== File name + extension (NEW) =====
    private TextField fileNameField;
    private ComboBox<String> extensionBox;

    private ProgressBar progressBar;
    private Label progressText;

    // ===== Speed UI =====
    private TextField speedField;
    private Label realSpeedLabel;

    // ===== Speed calc =====
    private long lastBytes = 0;
    private long lastTime = 0;

    private long currentTaskId = -1;

    private double xOffset;
    private double yOffset;

    @Override
    public void start(Stage stage) {

        settings = AppSettings.load();

        LocalStorage storage =
                new SQLiteStorage("src/main/java/org/example/data/downloadmanager.db");
        storage.init();

        manager = new DownloadManager(
                storage,
                new HttpDownloader(),
                new SpeedControl(0),
                new SegmentManager()
        );

        manager.addObserver(new UiObserver());

        stage.initStyle(StageStyle.UNDECORATED);

        // ===== Title bar =====
        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: black;");
        titleBar.setPadding(new Insets(6));
        titleBar.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = createWindowButton("✕");
        Button minimizeBtn = createWindowButton("—");

        closeBtn.setOnAction(e -> Platform.exit());
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        titleBar.getChildren().addAll(minimizeBtn, closeBtn);

        titleBar.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });

        // ===== Left bar =====
        VBox leftBar = new VBox(15);
        leftBar.setPadding(new Insets(20));
        leftBar.setPrefWidth(200);
        leftBar.setStyle("-fx-background-color: " + COLOR_ASH + ";");

        Label menuTitle = createLabel("Download\nManager", 16);

        Button resumeBtn = createMenuButton("Resume downloads");
        Button settingsBtn = createMenuButton("Settings");
        settingsBtn.setOnAction(e -> showSettingsWindow(stage));

        leftBar.getChildren().addAll(menuTitle, resumeBtn, settingsBtn);

        // ===== Content =====
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: " + COLOR_OAT + ";");

        urlField = createTextField("Download URL");

        // ===== File name + extension =====
        fileNameField = createTextField("File name");

        extensionBox = new ComboBox<>();
        extensionBox.getItems().addAll(
                "mp4",
                "webm",
                "mov",
                "jpg",
                "png",
                "gif",
                "pdf",
                "zip",
                "bin"
        );

        extensionBox.setValue("bin");

        HBox fileRow = new HBox(10, fileNameField, extensionBox);

        // HEAD-detect on URL change
        urlField.textProperty().addListener((obs, o, n) -> detectExtension(n));

        Button addBtn = createActionButton("Add");
        Button startBtn = createActionButton("Start");
        Button pauseBtn = createActionButton("Pause");
        Button resumeDownloadBtn = createActionButton("Resume");
        Button stopBtn = createActionButton("Stop");

        HBox buttons =
                new HBox(10, addBtn, startBtn, pauseBtn, resumeDownloadBtn, stopBtn);

        // ===== Speed =====
        speedField = createTextField("MB/s (0–20)");
        speedField.setPrefWidth(90);

        realSpeedLabel = new Label("Actual: 0.00 MB/s");
        realSpeedLabel.setTextFill(Color.web(COLOR_ASH));

        speedField.textProperty().addListener((obs, o, n) -> applySpeedLimit(n));

        HBox speedBox = new HBox(10, speedField, realSpeedLabel);

        // ===== Progress =====
        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(14);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        progressBar.setStyle("""
                -fx-accent: %s;
                -fx-control-inner-background: #2b2b2b;
                -fx-background-radius: 6;
                """.formatted(COLOR_ASH));

        progressText = createLabel("Progress: 0%", 12);
        progressText.setTextFill(Color.web(COLOR_ASH));

        VBox progressBox = new VBox(6, progressText, progressBar);

        // ===== Log =====
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("""
                -fx-control-inner-background: #2b2b2b;
                -fx-text-fill: white;
                -fx-font-size: 12;
                """);

        content.getChildren().addAll(
                createLabel("URL", 12),
                urlField,
                createLabel("File", 12),
                fileRow,
                buttons,
                speedBox,
                progressBox,
                createLabel("Log", 12),
                logArea
        );

        // ===== Actions =====
        addBtn.setOnAction(e -> addTask());
        startBtn.setOnAction(e -> {
            if (currentTaskId >= 0) manager.start(currentTaskId);
        });
        pauseBtn.setOnAction(e -> {
            if (currentTaskId >= 0) manager.pause(currentTaskId);
        });
        resumeDownloadBtn.setOnAction(e -> {
            if (currentTaskId >= 0) manager.resume(currentTaskId);
        });
        stopBtn.setOnAction(e -> {
            if (currentTaskId >= 0) manager.stop(currentTaskId);
        });

        BorderPane main = new BorderPane();
        main.setTop(titleBar);
        main.setLeft(leftBar);
        main.setCenter(content);

        stage.setScene(new Scene(main, 900, 520, Color.BLACK));
        stage.show();

        log("Download folder: " + settings.getDownloadDir());
    }

    // ===== HEAD detect (SAFE) =====
    private void detectExtension(String url) {
        if (url == null || url.isBlank()) return;

        new Thread(() -> {
            if (manager.getDownloader() instanceof HttpDownloader hd) {
                String ext = hd.detectExtensionByHead(url.trim());
                Platform.runLater(() -> extensionBox.setValue(ext));
            }
        }).start();
    }

    // ===== Add task (WORKING) =====
    private void addTask() {
        if (urlField.getText().isBlank() || fileNameField.getText().isBlank()) {
            log("URL or file name is empty");
            return;
        }

        String finalName =
                fileNameField.getText().trim() + "." + extensionBox.getValue();

        String filePath =
                settings.getDownloadDir().resolve(finalName).toString();

        DownloadTask task =
                manager.addDownload(urlField.getText().trim(), filePath);

        currentTaskId = task.getId();

        lastBytes = 0;
        lastTime = 0;

        setProgressUi(task);
        log("Task created: " + filePath);
    }

    // ===== Speed logic =====
    private void applySpeedLimit(String text) {
        try {
            if (text == null || text.isBlank()) {
                manager.setSpeedLimitBytesPerSec(0);
                return;
            }

            double mb = Double.parseDouble(text);
            if (mb <= 0 || mb > 20) {
                manager.setSpeedLimitBytesPerSec(0);
                return;
            }

            manager.setSpeedLimitBytesPerSec((long) (mb * 1024 * 1024));

        } catch (Exception e) {
            manager.setSpeedLimitBytesPerSec(0);
        }
    }

    // ===== Real speed =====
    private void updateRealSpeed(DownloadTask task) {
        long now = System.currentTimeMillis();

        if (lastTime == 0) {
            lastTime = now;
            lastBytes = task.getDownloadedBytes();
            return;
        }

        long dt = now - lastTime;
        if (dt < 500) return;

        long delta = task.getDownloadedBytes() - lastBytes;
        if (delta < 0) return;

        double speed = (delta * 1000.0 / dt) / (1024 * 1024);
        realSpeedLabel.setText(String.format("Actual: %.2f MB/s", speed));

        lastTime = now;
        lastBytes = task.getDownloadedBytes();
    }

    // ===== Settings =====
    private void showSettingsWindow(Stage owner) {
        Stage s = new Stage();
        s.initOwner(owner);
        s.initModality(Modality.WINDOW_MODAL);

        TextField dirField = new TextField(settings.getDownloadDir().toString());
        Button browse = new Button("Browse...");
        browse.setOnAction(e -> {
            DirectoryChooser ch = new DirectoryChooser();
            ch.setInitialDirectory(settings.getDownloadDir().toFile());
            File f = ch.showDialog(s);
            if (f != null) dirField.setText(f.getAbsolutePath());
        });

        Button save = new Button("Save");
        save.setOnAction(e -> {
            Path p = Paths.get(dirField.getText());
            if (p.toFile().exists()) {
                settings.setDownloadDir(p);
                settings.save();
                log("Settings saved: " + p);
                s.close();
            }
        });

        VBox root = new VBox(10, dirField, browse, save);
        root.setPadding(new Insets(12));

        s.setScene(new Scene(root));
        s.showAndWait();
    }

    private void setProgressUi(DownloadTask task) {
        Platform.runLater(() -> {
            if (task.getTotalBytes() <= 0) {
                progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                progressText.setText("Progress: ...");
            } else {
                double p = task.getProgress01();
                progressBar.setProgress(p);
                progressText.setText(
                        "Progress: " + (int) (p * 100) + "% (" +
                                task.getDownloadedBytes() + "/" + task.getTotalBytes() + ")"
                );
            }
            updateRealSpeed(task);
        });
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    // ===== UI helpers =====
    private Button createActionButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + COLOR_ASH + "; -fx-text-fill: white;");
        return b;
    }

    private Button createMenuButton(String text) {
        Button b = new Button(text);
        b.setPrefHeight(52);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color: " + COLOR_OAT + "; -fx-text-fill: white;");
        return b;
    }

    private Button createWindowButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        return b;
    }

    private Label createLabel(String text, int size) {
        Label l = new Label(text);
        l.setTextFill(Color.WHITE);
        l.setStyle("-fx-font-size: " + size + ";");
        return l;
    }

    private TextField createTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
        return tf;
    }

    // ===== Observer =====
    private class UiObserver implements DownloadObserver {
        @Override
        public void onTaskChanged(DownloadTask task) {
            if (task.getId() != currentTaskId) return;
            setProgressUi(task);
        }

        @Override
        public void onLog(String message) {
            log(message);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
