package com.inuwara.pdf_ink_cost_estimator;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;


@SpringBootApplication
public class PdfInkCostEstimatorApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        // Launches JavaFX runtime
        launch(args);
    }

    @Override
    public void init() {
        // Boots Spring context and embedded server before the window opens
        springContext = SpringApplication.run(PdfInkCostEstimatorApplication.class);
    }

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        
        // Connects JavaFX browser component to the local Spring Boot server
        engine.load("http://localhost:8080");

        Scene scene = new Scene(webView, 1280, 700);
        stage.setScene(scene);
        stage.setTitle("PDF Cost Estimator");
        stage.show();
        
    }

    @Override
    public void stop() {
        // Shuts down Spring Boot and frees memory when the window is closed
        if (springContext != null) {
            springContext.close();
        }
        System.exit(0);
    }
}



