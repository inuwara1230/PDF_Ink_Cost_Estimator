package com.inuwara.pdf_ink_cost_estimator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Renderer;

import java.awt.Color;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    @GetMapping(value = "/progress/{jobId}", produces = "text/event-stream")
    public SseEmitter streamProgress(@PathVariable String jobId) {
        // 10-minute timeout for massive files
        SseEmitter emitter = new SseEmitter(600000L); 
        emitters.put(jobId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        emitter.onError((e) -> emitters.remove(jobId));
        
        return emitter;
    }




    @PostMapping("/upload_pdf")
    public ResponseEntity<String> handlePdfUpload(
        @RequestParam("pdfDocument") MultipartFile file,
        @RequestParam(value = "colorCapability", required = false, defaultValue = "color") String colorCapability,
        @RequestParam(value = "jobID", required = false) String jobID,
        @RequestParam(value = "blackPrice", required = false, defaultValue = "0") double blackPrice,
        @RequestParam(value = "blackYield", required = false, defaultValue = "1") double blackYield,
        @RequestParam(value = "isoCoverage", required = false, defaultValue = "5") double isoCoverage,
        @RequestParam(value = "cyanPrice", defaultValue = "0") double cyanPrice,
        @RequestParam(value = "cyanYield", defaultValue = "1") double cyanYield,
        @RequestParam(value = "magentaPrice", defaultValue = "0") double magentaPrice,
        @RequestParam(value = "magentaYield", defaultValue = "1") double magentaYield,
        @RequestParam(value = "yellowPrice", defaultValue = "0") double yellowPrice,
        @RequestParam(value = "yellowYield", defaultValue = "1") double yellowYield,
        @RequestParam(value = "paperCost", required = false, defaultValue = "0") double paperCost)
     {
        // 1. Verify a file was actually sent
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        // 2. Verify it is a PDF
        if (!"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().body("Only PDF files are allowed.");
        }

        try{

            double fileSizeInBytes = file.getSize();
            byte[] fileBytes = file.getBytes();
            new Thread(() -> processPDFinBackground(fileBytes, colorCapability, jobID, fileSizeInBytes, blackPrice, blackYield, isoCoverage, cyanPrice, cyanYield, magentaPrice, magentaYield, yellowPrice, yellowYield, paperCost)).start();
            return ResponseEntity.ok("Upload received, processing started...");
        }
        catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to read file bytes.");
        }
    }

    private void processPDFinBackground(byte[] fileBytes, String colorCapability, String jobId, double fileSizeInBytes, double blackPrice, double blackYield, double isoCoverage, double cyanPrice, double cyanYield, double magentaPrice, double magentaYield, double yellowPrice, double yellowYield,double paperCost) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return; // Client disconnected

        boolean isMonochrome = "Monochrome".equalsIgnoreCase(colorCapability);

        try(PDDocument document = Loader.loadPDF(fileBytes)) {

            long startTime = System.currentTimeMillis();
            double fileSizeInMB =(double)fileSizeInBytes / (1024.0 * 1024.0);
            String fileSizeMessage = String.format("File size: %.2f MB", fileSizeInMB);
            
            PDFRenderer renderer = new PDFRenderer(document);
            
            int totalPages = document.getNumberOfPages();
            emitter.send("analyzing " + totalPages + " pages of the PDF file...");
            //System.out.println("Received file: " + document.getOriginalFilename()+ "size : " + fileSizeMessage);

            
            System.out.println("Total # pages: " + totalPages);
            final double standard_A4_Area =500990.0; // Standard US Letter size in square inches
            double totalAreaMultiplier = 0.0;

            double totalCyan = 0.0;
            double totalMagenta = 0.0;
            double totalYellow = 0.0;
            double totalBlack = 0.0;

            double cyanCost = 0.0;
            double magentaCost = 0.0;
            double yellowCost = 0.0;
            double blackCost = 0.0;

            for(int i=0; i<totalPages;i++){
                emitter.send("Processing page " + (i+1) + " of " + totalPages + "...");

                PDPage page = document.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();
                double pageArea =  mediaBox.getWidth() * mediaBox.getHeight() ;
                totalAreaMultiplier = (pageArea / standard_A4_Area);
                BufferedImage image =renderer.renderImageWithDPI(i, 150); // Render at 150 DPI

                int width = image.getWidth();
                int height = image.getHeight();  

                double totalPixels = width * height;

                double pageCyan = 0.0, pageMagenta = 0.0, pageYellow = 0.0, pageBlack = 0.0;

                for (int x=0;x<width;x++){
                    for(int y=0;y<height;y++){
                        Color pixelColor = new Color(image.getRGB(x,y));
                        // Normalize RGB values to [0,1]
                        double r = pixelColor.getRed() / 255.0;
                        double g = pixelColor.getGreen() / 255.0;
                        double b = pixelColor.getBlue() / 255.0;
                        if(isMonochrome){
                            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
                            double k = 1 - luminance;
                            pageBlack += k;
                        }else{
                            double k = 1 - Math.max(r, Math.max(g, b));
                            double c=0, m=0, y1=0;
                            if (k<1){
                                c = (1 - r - k) / (1 - k);
                                m = (1 - g - k) / (1 - k);
                                y1 = (1 - b - k) / (1 - k);
                            }
                            pageCyan += c;
                            pageMagenta += m;
                            pageYellow += y1;
                            pageBlack += k;
                        }

                    }
                }
                if(!isMonochrome){
                    totalCyan += (pageCyan / totalPixels)*100*totalAreaMultiplier;
                    totalMagenta += (pageMagenta / totalPixels)*100*totalAreaMultiplier;
                    totalYellow += (pageYellow / totalPixels)*100*totalAreaMultiplier;
                }
                totalBlack += (pageBlack / totalPixels)*100*totalAreaMultiplier;

            }

            cyanCost = isMonochrome ? 0 :totalCyan * (cyanPrice/(cyanYield*isoCoverage)); // Assuming $0.05 per % of cyan used
            magentaCost = isMonochrome ? 0 :totalMagenta * (magentaPrice/(magentaYield*isoCoverage));
            yellowCost = isMonochrome ? 0 :totalYellow * (yellowPrice/(yellowYield*isoCoverage));
            blackCost = totalBlack * (blackPrice/(blackYield*isoCoverage));

            double totalInkCost = cyanCost + magentaCost + yellowCost + blackCost;
            String inkCostMessage = String.format("Total Ink Cost: %.2f (Cyan: %.2f, Magenta: %.2f, Yellow: %.2f, Black: %.2f)", totalInkCost, cyanCost, magentaCost, yellowCost, blackCost);
            
            double totalPaperCost = paperCost * totalPages;
            String paperCostMessage = String.format("Total Paper Cost: %.2f", totalPaperCost);

            double totalCost = totalInkCost + totalPaperCost;
            String totalCostMessage = String.format("Total Cost: %.2f", totalCost);

            long endTime = System.currentTimeMillis();
            long processingTime = (endTime - startTime)/1000;

            String inkUsageMessage = String.format("Total Ink Usage - Cyan: %.2f%%, Magenta: %.2f%%, Yellow: %.2f%%, Black: %.2f%%",
                    totalCyan, totalMagenta, totalYellow, totalBlack);
            //----Pass the 'file' to Apache PDFBox for rasterization and pixel analysis-----

            System.out.println(inkUsageMessage);
            System.out.println("Processing Time: " + processingTime + " s");

            emitter.send(fileSizeMessage +"#Pages: " + totalPages + "\n" 
             + inkUsageMessage + "\n Processing Time: " +
              processingTime + " s \n" + inkCostMessage + "\n" + paperCostMessage + "\n" + totalCostMessage);
            

        } catch (Exception e) {
            ///e.printStackTrace();
            //emitter.completeWithError(e);
        }
    }
    
}
