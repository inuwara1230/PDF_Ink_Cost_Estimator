package com.inuwara.pdf_ink_cost_estimator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentController {

    @PostMapping("/upload_pdf")
    public ResponseEntity<String> handlePdfUpload(@RequestParam("pdfDocument") MultipartFile file) {
        // 1. Verify a file was actually sent
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        // 2. Verify it is a PDF
        if (!"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().body("Only PDF files are allowed.");
        }

        try {

            double fileSizeInMB = (double) file.getSize() / (1024.0 * 1024.0);
            String fileSizeMessage = String.format("File size: %.2f MB", fileSizeInMB);

            // For now, just print the file details to the console to verify it worked
            System.out.println("Received file: " + file.getOriginalFilename());
            

            //----Pass the 'file' to Apache PDFBox for rasterization and pixel analysis-----

            System.out.println("size : " + fileSizeMessage);

            return ResponseEntity.ok("PDF uploaded successfully! " + fileSizeMessage);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to process file.");
        }
    }
}
