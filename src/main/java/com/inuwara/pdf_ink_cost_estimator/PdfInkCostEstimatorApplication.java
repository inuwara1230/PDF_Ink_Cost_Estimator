package com.inuwara.pdf_ink_cost_estimator;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PdfInkCostEstimatorApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage splashStage;

    public static void main(String[] args) {
        launch(args);
    }

    // No init() any more: it blocked the UI while Spring booted.

    @Override
    public void start(Stage mainStage) {
        mainStage.getIcons().add(new Image(getClass().getResource("/app_icon.png").toExternalForm()));
        showSplash();

        // Boot Spring off the UI thread so the GIF keeps animating
        Task<ConfigurableApplicationContext> bootTask = new Task<>() {
            @Override
            protected ConfigurableApplicationContext call() {
                return SpringApplication.run(PdfInkCostEstimatorApplication.class,
                    "--server.port=0",
                    "--server.address=127.0.0.1"
                );
            }
        };

        bootTask.setOnSucceeded(e -> {
            springContext = bootTask.getValue();
            openMainWindow(mainStage);
        });

        bootTask.setOnFailed(e -> {
            bootTask.getException().printStackTrace();
            closeSplash();
            Platform.exit();
        });

        Thread t = new Thread(bootTask, "spring-boot-startup");
        t.setDaemon(true);
        t.start();
    }

    private void showSplash() {
        Image gif = new Image(getClass().getResource("/splash.gif").toExternalForm());
        ImageView view = new ImageView(gif);

        StackPane root = new StackPane(view);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, gif.getWidth(), gif.getHeight());
        scene.setFill(Color.TRANSPARENT);

        splashStage = new Stage(StageStyle.TRANSPARENT); // no title bar, no borders
        splashStage.getIcons().add(new Image(getClass().getResource("/app_icon.png").toExternalForm()));
        splashStage.setScene(scene);
        splashStage.centerOnScreen();
        splashStage.show();
    }

    private void openMainWindow(Stage mainStage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        mainStage.setScene(new Scene(webView, 1280, 700));
        mainStage.setTitle("Ink Cost calculator");

        // Only reveal the window once the page has rendered
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {
                mainStage.show();
                closeSplash();
            }
        });

        String port = springContext.getEnvironment().getProperty("local.server.port");
        engine.load("http://localhost:" + port);
    }

    private void closeSplash() {
        if (splashStage != null) {
            splashStage.close();
            splashStage = null;
        }
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        System.exit(0);
    }
}