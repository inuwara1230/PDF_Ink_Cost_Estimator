package com.inuwara.pdf_ink_cost_estimator.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct; 
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;


@RestController
@RequestMapping("/api/logo")
public class LogoController {

    private final Path logoStorageDir = Paths.get(System.getProperty("user.home"), ".ink_estimator", "logo");
    
    // We no longer hardcode the extension here, just the base name
    private final String baseFileName = "shop-logo";

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(logoStorageDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create logo directory!", e);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return ResponseEntity.badRequest().body("Please select a valid image file.");
            }

            // Determine the extension based on the uploaded file's content type
            String extension = file.getContentType().equals("image/jpeg") ? ".jpg" : ".png";
            Path newLogoPath = logoStorageDir.resolve(baseFileName + extension);
            Path oldLogoPath = logoStorageDir.resolve(baseFileName + (extension.equals(".jpg") ? ".png" : ".jpg"));

            // Delete the old opposite-format logo if it exists, so we don't have two logos
            Files.deleteIfExists(oldLogoPath);

            // Save the new logo
            Files.copy(file.getInputStream(), newLogoPath, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok("Logo updated successfully");
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to save logo.");
        }
    }

    @GetMapping
    public ResponseEntity<byte[]> getLogo() throws IOException {
        Path pngPath = logoStorageDir.resolve(baseFileName + ".png");
        Path jpgPath = logoStorageDir.resolve(baseFileName + ".jpg");

        // Check which file exists and return it with the correct content type
        if (Files.exists(pngPath)) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(pngPath));
        } else if (Files.exists(jpgPath)) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(Files.readAllBytes(jpgPath));
        } else {
            // Serve default
            ClassPathResource defaultLogo = new ClassPathResource("static/default-logo.png");
            if (defaultLogo.exists()) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .body(StreamUtils.copyToByteArray(defaultLogo.getInputStream()));
            }
            return ResponseEntity.ok().body(new byte[0]);
        }
    }
}