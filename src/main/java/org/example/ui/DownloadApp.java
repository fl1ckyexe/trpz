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
import java.util.List;

public class DownloadApp extends Application {

    private static final String COLOR_ASH = "#323030";
    private static final String COLOR_OAT = "#CDBEA7";

    private DownloadManager manager;
    private AppSettings settings;

    private TextArea logArea;
    private TextField urlField;
    private TextField fileNameField;
    private ComboBox<String> extensionBox;

    private ProgressBar progressBar;
    private Label progressText;

    // ===== Speed =====
    private TextField speedField;
    private Label realSpeedLabel;
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
                new SegmentManager(),
                settings
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
        resumeBtn.setOnAction(e -> showResumeWindow(stage));

        Button settingsBtn = createMenuButton("Settings");
        settingsBtn.setOnAction(e -> showSettingsWindow(stage));

        leftBar.getChildren().addAll(menuTitle, resumeBtn, settingsBtn);

        // ===== Content =====
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: " + COLOR_OAT + ";");

        urlField = createTextField("Download URL");

        fileNameField = createTextField("File name (without extension)");

        extensionBox = new ComboBox<>();
        extensionBox.getItems().addAll(
                "mp4", "webm", "mov",
                "jpg", "png", "gif",
                "pdf", "zip", "bin"
        );
        extensionBox.setValue("bin");

        urlField.textProperty().addListener((o, ov, nv) -> detectExtension(nv));

        HBox fileRow = new HBox(10, fileNameField, extensionBox);

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

        speedField.textProperty().addListener((o, ov, nv) -> applySpeedLimit(nv));

        HBox speedBox = new HBox(10, speedField, realSpeedLabel);

        // ===== Progress =====
        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(14);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        progressText = createLabel("Progress: 0%", 12);
        progressText.setTextFill(Color.web(COLOR_ASH));

        VBox progressBox = new VBox(6, progressText, progressBar);

        // ===== Log =====
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);

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
        startBtn.setOnAction(e -> startCurrent());
        pauseBtn.setOnAction(e -> pauseCurrent());
        resumeDownloadBtn.setOnAction(e -> resumeCurrent());
        stopBtn.setOnAction(e -> stopCurrent());

        BorderPane main = new BorderPane();
        main.setTop(titleBar);
        main.setLeft(leftBar);
        main.setCenter(content);

        stage.setScene(new Scene(main, 900, 520, Color.BLACK));
        stage.show();

        log("Download folder: " + settings.getDownloadDir());
        log("Incomplete folder: " + settings.getIncompleteDir());
    }

    // =========================
    // Resume window
    // =========================
    private void showResumeWindow(Stage owner) {
        Stage s = new Stage();
        s.initOwner(owner);
        s.initModality(Modality.WINDOW_MODAL);
        s.setTitle("Resume downloads");

        ListView<DownloadTask> list = new ListView<>();
        List<DownloadTask> unfinished = manager.getUnfinishedTasks();
        list.getItems().addAll(unfinished);

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(DownloadTask t, boolean empty) {
                super.updateItem(t, empty);
                if (t == null || empty) {
                    setText(null);
                } else {
                    setText(t.getFileName() + " [" + t.getStatus() + "]");
                }
            }
        });

        Button resumeBtn = new Button("Resume");
        resumeBtn.setOnAction(e -> {
            DownloadTask t = list.getSelectionModel().getSelectedItem();
            if (t != null) {
                currentTaskId = t.getId();
                manager.resume(t.getId());
                s.close();
            }
        });

        VBox root = new VBox(10, list, resumeBtn);
        root.setPadding(new Insets(12));

        s.setScene(new Scene(root, 420, 300));
        s.showAndWait();
    }

    // =========================
    // Download actions
    // =========================
    private void addTask() {
        if (urlField.getText().isBlank() || fileNameField.getText().isBlank()) {
            log("URL or file name is empty");
            return;
        }

        String finalName =
                fileNameField.getText().trim() + "." + extensionBox.getValue();

        Path finalPath = settings.getDownloadDir().resolve(finalName);

        DownloadTask task =
                manager.addDownload(urlField.getText().trim(), finalPath.toString());

        currentTaskId = task.getId();
        lastBytes = 0;
        lastTime = 0;

        log("Task created: " + finalPath);
    }

    private void startCurrent() {
        if (currentTaskId >= 0) manager.start(currentTaskId);
    }

    private void pauseCurrent() {
        if (currentTaskId >= 0) manager.pause(currentTaskId);
    }

    private void resumeCurrent() {
        if (currentTaskId >= 0) manager.resume(currentTaskId);
    }

    private void stopCurrent() {
        if (currentTaskId >= 0) manager.stop(currentTaskId);
    }

    // =========================
    // HEAD detect
    // =========================
    private void detectExtension(String url) {
        if (url == null || url.isBlank()) return;

        new Thread(() -> {
            if (manager.getDownloader() instanceof HttpDownloader hd) {
                String ext = hd.detectExtensionByHead(url.trim());
                Platform.runLater(() -> extensionBox.setValue(ext));
            }
        }).start();
    }

    // =========================
    // Speed
    // =========================
    private void applySpeedLimit(String text) {
        try {
            double mb = Double.parseDouble(text);
            if (mb <= 0 || mb > 20)
                manager.setSpeedLimitBytesPerSec(0);
            else
                manager.setSpeedLimitBytesPerSec((long) (mb * 1024 * 1024));
        } catch (Exception e) {
            manager.setSpeedLimitBytesPerSec(0);
        }
    }

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
        double speed = (delta * 1000.0 / dt) / (1024 * 1024);

        realSpeedLabel.setText(String.format("Actual: %.2f MB/s", speed));

        lastTime = now;
        lastBytes = task.getDownloadedBytes();
    }

    // =========================
    // Settings window
    // =========================
    private void showSettingsWindow(Stage owner) {
        Stage s = new Stage();
        s.initOwner(owner);
        s.initModality(Modality.WINDOW_MODAL);
        s.setTitle("Settings");

        TextField downloadDirField =
                new TextField(settings.getDownloadDir().toString());
        TextField incompleteDirField =
                new TextField(settings.getIncompleteDir().toString());

        Button browseDownload = new Button("Browse...");
        browseDownload.setOnAction(e -> {
            DirectoryChooser ch = new DirectoryChooser();
            File f = ch.showDialog(s);
            if (f != null) downloadDirField.setText(f.getAbsolutePath());
        });

        Button browseIncomplete = new Button("Browse...");
        browseIncomplete.setOnAction(e -> {
            DirectoryChooser ch = new DirectoryChooser();
            File f = ch.showDialog(s);
            if (f != null) incompleteDirField.setText(f.getAbsolutePath());
        });

        Button save = new Button("Save");
        save.setOnAction(e -> {
            settings.setDownloadDir(Paths.get(downloadDirField.getText()));
            settings.setIncompleteDir(Paths.get(incompleteDirField.getText()));
            settings.save();
            log("Settings saved");
            s.close();
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Download folder:"), 0, 0);
        grid.add(downloadDirField, 1, 0);
        grid.add(browseDownload, 2, 0);

        grid.add(new Label("Incomplete folder:"), 0, 1);
        grid.add(incompleteDirField, 1, 1);
        grid.add(browseIncomplete, 2, 1);

        VBox root = new VBox(12, grid, save);
        root.setPadding(new Insets(12));

        s.setScene(new Scene(root));
        s.showAndWait();
    }

    // =========================
    // UI helpers
    // =========================
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

    // =========================
    // Observer
    // =========================
    private class UiObserver implements DownloadObserver {
        @Override
        public void onTaskChanged(DownloadTask task) {
            if (task.getId() != currentTaskId) return;

            Platform.runLater(() -> {
                if (task.getTotalBytes() > 0) {
                    progressBar.setProgress(task.getProgress01());
                    progressText.setText(
                            "Progress: " +
                                    (int) (task.getProgress01() * 100) + "%"
                    );
                }
                updateRealSpeed(task);
            });
        }

        @Override
        public void onLog(String message) {
            log(message);
        }
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
