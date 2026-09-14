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
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//import javax.swing.Renderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.List;

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

        @RequestParam(value = "a4Cost", required = false, defaultValue = "0") double a4Cost,
        @RequestParam(value = "a3Cost", required = false, defaultValue = "0") double a3Cost,
        @RequestParam(value = "a5Cost", required = false, defaultValue = "0") double a5Cost,
        @RequestParam(value = "legalCost", required = false, defaultValue = "0") double legalCost,

        @RequestParam(value = "targetPaperSize", required = false, defaultValue = "A4") String targetPaperSize,
        @RequestParam(value = "printLayout", required = false, defaultValue = "standard") String printLayout,
        @RequestParam(value = "duplexMode", required = false, defaultValue = "simplex") String duplexMode,
        @RequestParam(value = "colorPageRange", required = false, defaultValue = "") String colorPageRange,
        @RequestParam(value = "monochromePageRange", required = false, defaultValue = "") String monochromePageRange)
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
            new Thread(() -> processPDFinBackground(fileBytes, colorCapability, jobID,
                 fileSizeInBytes, blackPrice, blackYield, isoCoverage, cyanPrice, cyanYield,
                  magentaPrice, magentaYield, yellowPrice, yellowYield, a4Cost, a3Cost, a5Cost, legalCost,
                targetPaperSize, printLayout, duplexMode, colorPageRange, monochromePageRange)).start();
            return ResponseEntity.ok("Upload received, processing started...");
        }
        catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to read file bytes.");
        }
    }

    private void processPDFinBackground(byte[] fileBytes, String colorCapability, 
        String jobId, double fileSizeInBytes, double blackPrice, double blackYield,
         double isoCoverage, double cyanPrice, double cyanYield, double magentaPrice, 
         double magentaYield, double yellowPrice, double yellowYield,double a4Cost, double a3Cost, double a5Cost, double legalCost,
         String targetPaperSize, String printLayout, String duplexMode, String colorPageRange, String monochromePageRange) {

        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return; // Client disconnected



        //boolean isMonochrome = "Monochrome".equalsIgnoreCase(colorCapability);

        try(PDDocument document = Loader.loadPDF(fileBytes)) {

            long startTime = System.currentTimeMillis();
            double fileSizeInMB =(double)fileSizeInBytes / (1024.0 * 1024.0);
            String fileSizeMessage = String.format("File size: %.2f MB", fileSizeInMB);
            
            PDFRenderer renderer = new PDFRenderer(document);
            
            int totalPages = document.getNumberOfPages();
            emitter.send("analyzing " + totalPages + " pages of the PDF file...");
            //System.out.println("Received file: " + document.getOriginalFilename()+ "size : " + fileSizeMessage);
            System.out.println("debug | " + targetPaperSize + " | " + printLayout + " | " + duplexMode );
            System.out.println("iso coverage: " + isoCoverage);

            
            List<List<Integer>> printJobs = parsePrintJobs(monochromePageRange, colorPageRange, totalPages);
            List<Integer> monoPages = printJobs.get(0);
            List<Integer> colorPages = printJobs.get(1);

            List<Integer> allPages = new ArrayList<>();
            allPages.addAll(colorPages);
            allPages.addAll(monoPages);

            

            // Print the raw strings coming from the frontend
            System.out.println("DEBUG - Raw Mono String from Frontend: " + monochromePageRange);
            System.out.println("DEBUG - Raw Color String from Frontend: " + colorPageRange);
            // Then run your parser
            
            // Print the resulting lists separately
            System.out.println("DEBUG - Parsed Mono List: " + monoPages);
            System.out.println("DEBUG - Parsed Color List: " + colorPages);


            
            System.out.println("Total # pages: " + allPages.size() + " | Monochrome: " + monoPages.size() + " | Color: " + colorPages.size());
            final double standard_A4_Area = 501135.0; 
            double totalAreaMultiplier = 0.0;

            double totalCyan = 0.0;
            double totalMagenta = 0.0;
            double totalYellow = 0.0;
            double totalBlack = 0.0;

            double cyanCost = 0.0;
            double magentaCost = 0.0;
            double yellowCost = 0.0;
            double blackCost = 0.0;

            double accumulated_pdf_page_area = 0.0;
            double total_pdf_pages = allPages.size();

            for(Integer i : allPages) {

                boolean isMono = monoPages.contains(i);
                boolean isColor = colorPages.contains(i);
                
                emitter.send("Processing Page Number : " + (i) + " | Monochrome: " + isMono + " | Color: " + isColor);

                PDPage page = document.getPage(i-1); // PDFBox uses 0-based indexing
                PDRectangle mediaBox = page.getMediaBox();
                double pageArea =  mediaBox.getWidth() * mediaBox.getHeight() ;
                accumulated_pdf_page_area += pageArea;

                totalAreaMultiplier = (pageArea / standard_A4_Area);


                // Lowering DPI to 50 or 72 is highly recommended. 
                // Color coverage percentages remain almost identical, but it processes 9x faster than 150 DPI.
                BufferedImage image = renderer.renderImageWithDPI(i - 1, 150); 

                int width = image.getWidth();
                int height = image.getHeight();  
                double totalPixels = width * height;

                double pageCyan = 0.0, pageMagenta = 0.0, pageYellow = 0.0, pageBlack = 0.0;

                // Grab all pixels in one pass into a 1D array
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

                for (int pixel : pixels) {
                    // Fast bitwise extraction of RGB (skipping alpha channel)
                    double r = ((pixel >> 16) & 0xff) / 255.0;
                    double g = ((pixel >> 8) & 0xff) / 255.0;
                    double b = (pixel & 0xff) / 255.0;
                
                    if (isMono) {
                        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
                        pageBlack += (1.0 - luminance);
                    } else if (isColor) {
                        double k = 1.0 - Math.max(r, Math.max(g, b));

                        if (k < 1.0) {
                            pageCyan += (1.0 - r - k) / (1.0 - k);
                            pageMagenta += (1.0 - g - k) / (1.0 - k);
                            pageYellow += (1.0 - b - k) / (1.0 - k);
                        }
                        pageBlack += k;
                    }
                }

                // Clear the image from memory explicitly to prevent heap saturation
                image.flush();
                if(isColor){
                    totalCyan += (pageCyan / totalPixels)*100*totalAreaMultiplier;
                    totalMagenta += (pageMagenta / totalPixels)*100*totalAreaMultiplier;
                    totalYellow += (pageYellow / totalPixels)*100*totalAreaMultiplier;
                }
                totalBlack += (pageBlack / totalPixels)*100*totalAreaMultiplier;

            }



            double final_pdf_page_area = accumulated_pdf_page_area / total_pdf_pages;
            double ink_cost_multiplier = 0.0;

            
            double targetPaperArea = switch (targetPaperSize) {
                
                case "A3" -> 1002270.0;
                case "A5" -> 250567.0;
                case "Legal" -> 604800.0;
                default -> 501135.0; // Default to A4 if unknown
            };
            ink_cost_multiplier = targetPaperArea / final_pdf_page_area;

    

            cyanCost = totalCyan *ink_cost_multiplier* (cyanPrice/(cyanYield*isoCoverage)); 
            magentaCost = totalMagenta *ink_cost_multiplier* (magentaPrice/(magentaYield*isoCoverage));
            yellowCost = totalYellow *ink_cost_multiplier* (yellowPrice/(yellowYield*isoCoverage));
            blackCost = totalBlack *ink_cost_multiplier* (blackPrice/(blackYield*isoCoverage));

            System.out.println("DEBUG | Final PDF Page Area: " + final_pdf_page_area + " | Ink Cost Multiplier: " + ink_cost_multiplier);
            //++++++++++++++++++++++++++++++++++++++++++++++++++++
            //####################### print layout and and duplex mode cost calculations can be added here #######################
            //++++++++++++++++++++++++++++++++++++++++++++++++++++

            double totalInkCost = cyanCost + magentaCost + yellowCost + blackCost;
            //System.out.println("cost without multiplieng: " + totalInkCost/ink_cost_multiplier);
            String inkCostMessage = String.format("Total Ink Cost after scalling: %.2f (Cyan: %.2f, Magenta: %.2f, Yellow: %.2f, Black: %.2f)", totalInkCost, cyanCost, magentaCost, yellowCost, blackCost);
            
            //double totalPaperCost = 2 * totalPages;
            String paperCostMessage = String.format("paper cost yet to be implemented");

            double totalCost = totalInkCost; //+ totalPaperCost;
            String totalCostMessage = String.format("Total Cost after scalling: %.2f", totalCost);

            long endTime = System.currentTimeMillis();
            long processingTime = (endTime - startTime)/1000;

            String inkUsageMessage = String.format("Total Ink Usage - Cyan: %.2f%%, Magenta: %.2f%%, Yellow: %.2f%%, Black: %.2f%%",
                    totalCyan, totalMagenta, totalYellow, totalBlack);
            

            System.out.println(inkUsageMessage);
            System.out.println("Processing Time: " + processingTime + " s");

            emitter.send(fileSizeMessage +" | Analyzed Number of Pages: " + allPages.size() + "/" + totalPages + "\n" 
            + "Monochrome Pages: " + monoPages.size() + " | Color Pages: " + colorPages.size() + "\n"
             + inkUsageMessage + "\n Processing Time: " +
              processingTime + " s \n" 
              + inkCostMessage + "\n" 
              + paperCostMessage + "\n"
               + totalCostMessage );
            

        } catch (Exception e) {
            ///e.printStackTrace();
            //emitter.completeWithError(e);
        }
    }


public List<List<Integer>> parsePrintJobs(String monoInput, String colorInput, int totalPages) {
    List<List<Integer>> result = new ArrayList<>();

    boolean isMonoEmpty = (monoInput == null || monoInput.trim().isEmpty());
    boolean isColorEmpty = (colorInput == null || colorInput.trim().isEmpty());

    // Rule 1: If both strings are empty, the whole document is printed in mono
    if (isMonoEmpty && isColorEmpty) {
        List<Integer> fullMonoList = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            fullMonoList.add(i);
        }
        
        result.add(fullMonoList);       // Index 0: Mono list (Full)
        result.add(new ArrayList<>());  // Index 1: Color list (Empty)
        return result;
    }

    // Rule 2: Parse both strings into Sets (Sets automatically prevent duplicates)
    Set<Integer> colorPages = parseSingleString(colorInput, totalPages);
    Set<Integer> monoPages = parseSingleString(monoInput, totalPages);

    // Rule 3: Color has priority. Remove any page from Mono that already exists in Color.
    monoPages.removeAll(colorPages);

    // Convert the Sets back to standard Lists and sort them numerically
    List<Integer> finalMonoList = new ArrayList<>(monoPages);
    Collections.sort(finalMonoList);

    List<Integer> finalColorList = new ArrayList<>(colorPages);
    Collections.sort(finalColorList);

    // Add to the final nested list and return
    result.add(finalMonoList);
    result.add(finalColorList);

    return result;
}

private Set<Integer> parseSingleString(String input, int totalPages) {
    Set<Integer> pages = new HashSet<>();

    if (input == null || input.trim().isEmpty()) {
        return pages;
    }

    for (String part : input.split(",")) {
        part = part.trim();

        if (part.contains("-")) {
            if (part.endsWith("-")) {
                try {
                    String startStr = part.substring(0, part.length() - 1).trim();
                    int start = Integer.parseInt(startStr);
                    for (int i = start; i <= totalPages; i++) {
                        pages.add(i);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        end = Math.min(end, totalPages);
                        for (int i = start; i <= end; i++) {
                            pages.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } else {
            try {
                int page = Integer.parseInt(part);
                if (page <= totalPages) {
                    pages.add(page);
                }
            } catch (NumberFormatException ignored) {}
        }
    }
    return pages;
}
    
}
