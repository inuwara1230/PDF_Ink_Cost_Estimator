package com.inuwara.pdf_ink_cost_estimator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

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

        try(PDDocument document = Loader.loadPDF(file.getBytes())) {

            double fileSizeInMB = (double) file.getSize() / (1024.0 * 1024.0);
            String fileSizeMessage = String.format("File size: %.2f MB", fileSizeInMB);

            // For now, just print the file details to the console to verify it worked
            System.out.println("Received file: " + file.getOriginalFilename()+ "size : " + fileSizeMessage);
            
            int totalPages = document.getNumberOfPages();
            System.out.println("totae # pages"+ totalPages);

            for(int i=0; i<totalPages;i++){
                PDPage page = document.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();


                double pageSize =  mediaBox.getWidth() * mediaBox.getHeight() ;
            }

            //----Pass the 'file' to Apache PDFBox for rasterization and pixel analysis-----

            

            return ResponseEntity.ok("PDF uploaded successfully! " + fileSizeMessage);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to process file.");
        }
    }
}
