package com.inuwara.pdf_ink_cost_estimator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;


//import jakarta.annotation.PostConstruct;

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;

import org.springframework.util.StreamUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.Color;
import java.awt.Desktop;
import java.util.concurrent.ConcurrentHashMap;


//import java.awt.Desktop;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;



@RestController
@RequestMapping("/api")
public class DocumentController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // Store finished calculation reports mapped by jobId
    private final Cache<String, CalculationReport> reports = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    


    @GetMapping("/icc_profiles")
    public ResponseEntity<List<String>> getIccProfiles() {
        List<String> profileNames = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            // Scan for all files in the profiles classpath folder
            Resource[] resources = resolver.getResources("classpath:profiles/*");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && (filename.toLowerCase().endsWith(".icc") || filename.toLowerCase().endsWith(".icm"))) {
                    try (InputStream is = resource.getInputStream()) {
                        // Attempt to parse, but fallback to including the filename if java.awt strict validation fails
                        ICC_Profile profile = ICC_Profile.getInstance(is);
                        if (profile != null) {
                            profileNames.add(filename);
                            continue;
                        }
                    } catch (Exception e) {
                        System.err.println("Notice: Skipping strict Java AWT parsing for " + filename + " (adding by extension match).");
                    }
                    // Add the file to the dropdown list based on valid extension
                    profileNames.add(filename);
                }
            }
            return ResponseEntity.ok(profileNames);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // Dynamic ICC Profile loader per request
    private ColorConvertOp createCmykConvertOp(String profileName) {
        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = "USWebUncoated.icc";
        }
        try (InputStream is_ = new ClassPathResource("profiles/" + profileName).getInputStream()) {
            ICC_Profile cmykProfile = ICC_Profile.getInstance(is_);
            ICC_ColorSpace cmykSpace = new ICC_ColorSpace(cmykProfile);
            return new ColorConvertOp(cmykSpace, null);
        } catch (Exception e) {
            System.err.println("Warning: Failed to load ICC profile (" + profileName + "): " + e.getMessage());
            return null;
        }
    }

    //private ColorConvertOp cmykConvertOp;

    // Load the ICC profile once into memory when the application starts
    //@PostConstruct
    //public void initProfile() {
    //    try (InputStream is = new ClassPathResource("profiles/USWebUncoated.icc").getInputStream()) {
    //        ICC_Profile cmykProfile = ICC_Profile.getInstance(is);
    //        ICC_ColorSpace cmykSpace = new ICC_ColorSpace(cmykProfile);
    //        this.cmykConvertOp = new ColorConvertOp(cmykSpace, null);
    //    } catch (Exception e) {
    //        System.err.println("Warning: Failed to load USWebCoatedSWOP.icc profile: " + e.getMessage());
    //    }
    //}
    // Utility method to convert RGB to CMYK using the loaded ICC profile

    // Data Transfer Object for Report Generation
    public static class CalculationReport {
        public String jobId;
        public double fileSizeMB;
        public int totalPages;
        public int monoPagesCount;
        public int colorPagesCount;
        public double cyanUsage, magentaUsage, yellowUsage, blackUsage;
        public double cyanCost, magentaCost, yellowCost, blackCost;
        public double totalInkCost;
        public double inkCostAfterNup;
        public int numberOfPagesAfterNup;
        public double totalPaperCost;

        public int finalCost;

        public long processingTimeSeconds;
        public String targetPaperSize;
        public String printLayout;
        public String duplexMode;
        public double costOfASingle_PaperSheet;
        public String fileName; // Store the original file name for reference
        public List<Integer> monoPages;
        public List<Integer> colorPages;
        public double markupPercentage;
        public double minimumFee;
        public double finishingCost;
        public double discountPercentage;
        public double tieredMultiplier;
        public int customerFinalPrice;
        public int Shop_profit;
        public String shopName;
        public String shopAddress;
        public String shopContact;

        public String whichMethod_Used;
        public int pdf_page_count; // Store the number of pages analyzed for reporting

        public Double CostPerPage;
        public int N_up;

        
    }

    @GetMapping(value = "/progress/{jobId}", produces = "text/event-stream")
    public SseEmitter streamProgress(@PathVariable String jobId) {
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
        @RequestParam(value = "jobID", required = false) String jobID,
        @RequestParam(value = "iccProfile", required = false, defaultValue = "USWebUncoated.icc") String iccProfile,
        @RequestParam(value = "blackPrice", required = false, defaultValue = "0") double blackPrice,
        @RequestParam(value = "blackYield", required = false, defaultValue = "1") double blackYield,
        @RequestParam(value = "isoCoverage", required = false, defaultValue = "5") double isoCoverage,
        @RequestParam(value = "cyanPrice", defaultValue = "0") double cyanPrice,
        @RequestParam(value = "cyanYield", defaultValue = "1") double cyanYield,
        @RequestParam(value = "magentaPrice", defaultValue = "0") double magentaPrice,
        @RequestParam(value = "magentaYield", defaultValue = "1") double magentaYield,
        @RequestParam(value = "yellowPrice", defaultValue = "0") double yellowPrice,
        @RequestParam(value = "yellowYield", defaultValue = "1") double yellowYield,
        @RequestParam(value = "targetPaperSize", required = false, defaultValue = "A4") String targetPaperSize,
        @RequestParam(value = "targetPaperArea", required = false, defaultValue = "501135.0") double targetPaperArea,
        @RequestParam(value = "targetPaperCost", required = false, defaultValue = "0.0") double targetPaperCost,
        @RequestParam(value = "printLayout", required = false, defaultValue = "standard") String printLayout,
        @RequestParam(value = "duplexMode", required = false, defaultValue = "simplex") String duplexMode,
        @RequestParam(value = "colorPageRange", required = false, defaultValue = "") String colorPageRange,
        @RequestParam(value = "monochromePageRange", required = false, defaultValue = "") String monochromePageRange,
        @RequestParam (value = "markupPercentage", required = false, defaultValue = "100") double markupPercentage,
        @RequestParam (value = "minimumFee", required = false, defaultValue = "0") double minimumFee,
        @RequestParam (value = "finishingCost", required = false, defaultValue = "0") double finishingCost,
        @RequestParam (value = "discountPercentage", required = false, defaultValue = "0") double discountPercentage,
        @RequestParam (value = "nUpFactor", required = false, defaultValue = "1") double nUpFactor,
        @RequestParam (value = "shopName", required = false, defaultValue = "") String shopName,
        @RequestParam (value = "shopAddress", required = false, defaultValue = "") String shopAddress,
        @RequestParam (value = "shopContact", required = false, defaultValue = "") String shopContact)
    {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty.");
        if (!"application/pdf".equals(file.getContentType())) return ResponseEntity.badRequest().body("Only PDF files are allowed.");

        try {
            double fileSizeInBytes = file.getSize();
            String fileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
            byte[] fileBytes = file.getBytes();
            new Thread(() -> processPDFinBackground(fileBytes, fileName, jobID,iccProfile,
                 fileSizeInBytes, blackPrice, blackYield, isoCoverage, cyanPrice, cyanYield,
                  magentaPrice, magentaYield, yellowPrice, yellowYield,
                targetPaperSize, targetPaperArea, targetPaperCost, printLayout, duplexMode, colorPageRange,
                 monochromePageRange, markupPercentage, minimumFee, finishingCost, discountPercentage, nUpFactor, shopName, shopAddress, shopContact)).start();
            return ResponseEntity.ok("Upload received, processing started...");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to read file bytes.");
        }
    }

    private void processPDFinBackground(byte[] fileBytes, String fileName, 
        String jobId,String iccProfile, double fileSizeInBytes, double blackPrice, double blackYield,
        double isoCoverage, double cyanPrice, double cyanYield, double magentaPrice, 
        double magentaYield, double yellowPrice, double yellowYield,
        String targetPaperSize, double targetPaperArea, double targetPaperCost,
        String printLayout, String duplexMode, String colorPageRange, String monochromePageRange,
        double markupPercentage, double minimumFee, double finishingCost, double discountPercentage, double nUpFactor,
        String shopName, String shopAddress, String shopContact) {

        // Retry loop to account for SSE connection handshake latency
        SseEmitter emitter = null;
        for (int i = 0; i < 15; i++) {
            emitter = emitters.get(jobId);
            if (emitter != null) break;
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }


        if (emitter == null) {
            System.err.println("Failed to bind SSE emitter for Job ID: " + jobId);
            return;
        }

        

        // Instantiate profile converter dynamically for this job
        System.out.println("Loading ICC profile: " + iccProfile);
        ColorConvertOp cmykConvertOp = createCmykConvertOp(iccProfile);

        try (PDDocument document = Loader.loadPDF(fileBytes)) {

            if (document.isEncrypted()) {
                System.out.println("PDF is encrypted. Job ID: " + jobId);
                try {
                    emitter.send(SseEmitter.event().name("ERROR").data("This PDF is password protected or encrypted. Please upload an unlocked file."));
                } catch (Exception ignore) {}
                emitter.complete();
                return;
            }

            long startTime = System.currentTimeMillis();
            double fileSizeInMB = (double) fileSizeInBytes / (1024.0 * 1024.0);
            PDFRenderer renderer = new PDFRenderer(document);
            
            int totalPages = document.getNumberOfPages();
            emitter.send("Analyzing " + totalPages + " pages of the PDF file...");

            List<List<Integer>> printJobs = parsePrintJobs(monochromePageRange, colorPageRange, totalPages);
            List<Integer> monoPages = printJobs.get(0);
            List<Integer> colorPages = printJobs.get(1);

            List<Integer> allPages = new ArrayList<>();
            allPages.addAll(colorPages);
            allPages.addAll(monoPages);

            final double standard_A4_Area = 501135.0; 
            double totalAreaMultiplier = 0.0;

            double totalCyan = 0.0, totalMagenta = 0.0, totalYellow = 0.0, totalBlack = 0.0;

            double accumulated_pdf_page_area = 0.0;
            double total_pdf_pages = allPages.size();
            String whichMethod_Used ="";

            for (Integer i : allPages) {
                boolean isMono = monoPages.contains(i);
                boolean isColor = colorPages.contains(i);
                emitter.send("Processing Page Number: " + i + " | Monochrome: " + isMono + " | Color: " + isColor);

                PDPage page = document.getPage(i - 1);
                PDRectangle mediaBox = page.getMediaBox();
                double pageArea = mediaBox.getWidth() * mediaBox.getHeight();
                accumulated_pdf_page_area += pageArea;
                totalAreaMultiplier = (pageArea / standard_A4_Area);

                BufferedImage image = renderer.renderImageWithDPI(i - 1, 150); 
                int width = image.getWidth();
                int height = image.getHeight();  
                double totalPixels = width * height;

                double pageCyan = 0.0, pageMagenta = 0.0, pageYellow = 0.0, pageBlack = 0.0;

                
                //cmykConvertOp != null
                if(cmykConvertOp != null) {
                    BufferedImage cmykImage = cmykConvertOp.filter(image, null);
                    Raster cmykRaster = cmykImage.getRaster();

                    int pixel_[] = new int[4];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            cmykRaster.getPixel(x, y, pixel_);
                            if (isColor) {
                                pageCyan += pixel_[0] / 255.0;
                                pageMagenta += pixel_[1] / 255.0;
                                pageYellow += pixel_[2] / 255.0;
                            }
                            pageBlack += pixel_[3] / 255.0;
                        }
                    }
                    whichMethod_Used = iccProfile;
                    System.out.println("cmyk convertion happened");
                    cmykImage.flush();
                }else {
                    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

                    for (int pixel : pixels) {
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
                    whichMethod_Used = "Normal RGB Conversion";
                    System.out.println("normal rgb convertion happened");
                }
                
                

                image.flush();
                if (isColor) {
                    totalCyan += (pageCyan / totalPixels) * 100 * totalAreaMultiplier;
                    totalMagenta += (pageMagenta / totalPixels) * 100 * totalAreaMultiplier;
                    totalYellow += (pageYellow / totalPixels) * 100 * totalAreaMultiplier;
                }
                totalBlack += (pageBlack / totalPixels) * 100 * totalAreaMultiplier;
            }

            double final_pdf_page_area = accumulated_pdf_page_area / total_pdf_pages;
            
            // Replaced switch blocks with the dynamically calculated frontend values!
            double ink_cost_multiplier = targetPaperArea / final_pdf_page_area;

            System.out.print("iso coverage: "+ isoCoverage);

            double cyanCost = totalCyan * (cyanPrice / (cyanYield * isoCoverage)); 
            double magentaCost = totalMagenta * (magentaPrice / (magentaYield * isoCoverage));
            double yellowCost = totalYellow  * (yellowPrice / (yellowYield * isoCoverage));
            double blackCost = totalBlack  * (blackPrice / (blackYield * isoCoverage));

            double total_inkcost_befor_scalling = cyanCost + magentaCost + yellowCost + blackCost;

            double totalInkCost = total_inkcost_befor_scalling * ink_cost_multiplier;

            int N_up = switch (printLayout) {
                case "booklet", "2up" -> 2;
                case "4up" -> 4;
                case "6up" -> 6;
                case "8up" -> 8;
                case "9up" -> 9;
                case "16up" -> 16;
                case "custom" -> (int) nUpFactor;
                default -> 1;
            };

            System.out.println("N-up factor: " + N_up); 
            System.out.println("printLayout: " + printLayout);
            double ink_cost_after_N_up = totalInkCost / N_up;

            int duplexFactor = "duplex".equalsIgnoreCase(duplexMode) ? 2 : 1;

            int numberOfpagesAfterNup = (int) Math.ceil((double) allPages.size() / (N_up * duplexFactor));

            System.out.println("shop name: "+shopName+"| shop adress: "+ shopAddress+"| contact: "+shopContact);

            System.out.println("paper cost: " + targetPaperCost +"|" +"target paper name: " + targetPaperSize + " | target page area: " + targetPaperArea);

            // Dynamically computed total paper cost
            double totalPaperCost = numberOfpagesAfterNup * targetPaperCost;
            Double costOfASingle_PaperSheet = targetPaperCost; 

            long processingTime = (System.currentTimeMillis() - startTime) / 1000;

            int finalCost = (int) Math.ceil((ink_cost_after_N_up + totalPaperCost));

            Double tiered_Multiplier = 1.0;
            if (finalCost < 50) {
                tiered_Multiplier = 1.2; 
            } else if (finalCost < 100) {
                tiered_Multiplier = 1.1; 
            } else if (finalCost < 200) {
                tiered_Multiplier = 1.05; 
            }else if (finalCost < 500) {
                tiered_Multiplier = 1.02; 
            }else if (finalCost < 1000) {
                tiered_Multiplier = 1.01; 
            }else if (finalCost < 2000) {
                tiered_Multiplier = 1.005; 
            }else if (finalCost < 5000) {
                tiered_Multiplier = 1.0; 
            }else if (finalCost < 10000) {
                tiered_Multiplier = 0.99;
            } else if (finalCost < 20000) {
                tiered_Multiplier = 0.98;
            } else {
                tiered_Multiplier = 0.97; 
            }
            double markedUpCost = finalCost * (1+((markupPercentage / 100.0)*tiered_Multiplier));
            double subtotal = markedUpCost + finishingCost;

            if (subtotal < minimumFee) {
                subtotal = minimumFee;
            }

            Double customerPrice_before_ceiling = subtotal - (subtotal * (discountPercentage / 100.0));

            System.out.println("Subtotal before discount: " + customerPrice_before_ceiling);

            int customerFinalPrice = (int) Math.ceil(customerPrice_before_ceiling);

            int Shop_profit = customerFinalPrice - finalCost;

            Double costPerPage = (double) customerFinalPrice / allPages.size();

            CalculationReport report = new CalculationReport();
            report.jobId = jobId;
            report.fileSizeMB = fileSizeInMB;
            report.totalPages = allPages.size();
            report.monoPagesCount = monoPages.size();
            report.colorPagesCount = colorPages.size();
            report.cyanUsage = totalCyan;
            report.magentaUsage = totalMagenta;
            report.yellowUsage = totalYellow;
            report.blackUsage = totalBlack;
            report.cyanCost = cyanCost;
            report.magentaCost = magentaCost;
            report.yellowCost = yellowCost;
            report.blackCost = blackCost;
            report.totalInkCost = totalInkCost;
            report.inkCostAfterNup = ink_cost_after_N_up;
            report.numberOfPagesAfterNup = numberOfpagesAfterNup;
            report.totalPaperCost = totalPaperCost;
            report.finalCost = finalCost;
            report.processingTimeSeconds = processingTime;
            report.targetPaperSize = targetPaperSize;
            report.printLayout = printLayout;
            report.duplexMode = duplexMode;
            report.costOfASingle_PaperSheet = costOfASingle_PaperSheet;
            report.fileName = fileName;
            report.monoPages = monoPages;
            report.colorPages = colorPages;
            report.markupPercentage = markupPercentage;
            report.minimumFee = minimumFee;
            report.finishingCost = finishingCost;
            report.discountPercentage = discountPercentage;
            report.customerFinalPrice = customerFinalPrice;
            report.Shop_profit = Shop_profit;
            report.tieredMultiplier = tiered_Multiplier;
            report.shopName = shopName;
            report.shopAddress = shopAddress;
            report.shopContact = shopContact;
            report.whichMethod_Used = whichMethod_Used;
            report.pdf_page_count = totalPages;
            report.CostPerPage = costPerPage;
            report.N_up = N_up;
            

            reports.put(jobId, report);

            String summaryMessage = String.format("Calculated for Job ID: %s\n" +"File name: %s\n" +
                    "File size: %.2f MB | Analyzed: %d/%d\n" +
                    "Mono Pages: %d | Color Pages: %d\n" +
                    "Ink Usage - C: %.2f%%, M: %.2f%%, Y: %.2f%%, K: %.2f%%\n" +
                    "Processing Time: %d s\n" +
                    "Method Used: %s\n\n" +
                    "Ink Cost after scalling: %.2f\n" +
                    "Ink Cost after %d-up: %.2f\n\n" +
                    "number of Sheets after %d-up and %s mode: %d\n" +
                    "Cost per Sheet: %.2f\n" +
                    "Total Paper Cost: %.2f\n" +
                    "Final Cost(Ink + Paper): %d\n\n"+
                    "Markup: %.2f%%\n" +
                    "Minimum Fee: %.2f\n" +
                    "Finishing Cost: %.2f\n" +
                    "Discount: %.2f%%\n\n" +
                    "Cost per document page: %.2f\n" +
                    "Customer- Final Price: %d\n" +
                    "Shop Profit: %d",
                    jobId,fileName,fileSizeInMB, allPages.size(), totalPages,
                    monoPages.size(), colorPages.size(),totalCyan, totalMagenta, totalYellow, totalBlack,
                    processingTime,whichMethod_Used, totalInkCost, N_up,ink_cost_after_N_up,N_up, duplexMode,
                    numberOfpagesAfterNup, costOfASingle_PaperSheet, totalPaperCost,finalCost,
                     markupPercentage, minimumFee, finishingCost, discountPercentage,costPerPage,customerFinalPrice, Shop_profit);

            emitter.send(summaryMessage);
            emitter.send(SseEmitter.event().name("COMPLETE").data("Analysis complete!"));
            emitter.complete();

        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            try {
                // Send explicit error event over SSE
                emitter.send(SseEmitter.event().name("ERROR").data("This PDF is password protected. Please upload an unlocked file."));
            } catch (Exception ignore) {}
            // Gracefully close stream without handing exception back to Spring MVC
            emitter.complete();

        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("ERROR").data("Error analyzing PDF: " + e.getMessage()));
            } catch (Exception ignore) {}
            // Gracefully close stream without handing exception back to Spring MVC
            emitter.complete();
        
        }
    }



@PostMapping("/recalculate_profit")
public ResponseEntity<String> recalculateProfit(
        @RequestParam("jobID") String jobId,
        @RequestParam("discountPercentage") double discountPercentage) {

    CalculationReport report = reports.getIfPresent(jobId);
    if (report == null) {
        return ResponseEntity.badRequest().body("Job not found or has expired in cache.");
    }

    // Apply the new discount
    System.out.println("====Recalculating profit for Job ID: " + jobId + " with new discount: " + discountPercentage +"=====");

    report.discountPercentage = discountPercentage;

    System.out.println("Discount applied: " + report.discountPercentage);
    System.out.println("Final Cost before markup: " + report.finalCost);
    System.out.println("Markup Percentage: " + report.markupPercentage);
    System.out.println("Tiered Multiplier: " + report.tieredMultiplier);
    System.out.println("Finishing Cost: " + report.finishingCost);
    System.out.println("Minimum Fee: " + report.minimumFee);

    double markedUpCost = report.finalCost * (1 + (report.markupPercentage / 100.0) * report.tieredMultiplier);
    double subtotal = markedUpCost + report.finishingCost;

    if (subtotal < report.minimumFee) {
        subtotal = report.minimumFee;
    }

    double customerPriceBeforeCeiling = subtotal - (subtotal * (report.discountPercentage / 100.0));
    report.customerFinalPrice = (int) Math.ceil(customerPriceBeforeCeiling);
    report.CostPerPage = (double) report.customerFinalPrice / report.totalPages;
    report.Shop_profit = report.customerFinalPrice - report.finalCost;

    // Update the cache explicitly (optional but good practice)
    reports.put(jobId, report);

    // Broadcast the updated results through the existing SSE pipeline
   
    String summaryMessage = String.format("Recalculated for Job ID: %s\n" +
            "File name: %s\n" +
            "File size: %.2f MB | Analyzed: %d/%d\n" +
            "Mono Pages: %d | Color Pages: %d\n" +
            "Ink Usage - C: %.2f%%, M: %.2f%%, Y: %.2f%%, K: %.2f%%\n" +
            "Processing Time: %d s\n" +
            "Method Used: %s\n\n" +
            "Ink Cost after scalling: %.2f\n" +
            "Ink Cost after %d-up: %.2f\n\n" +
            "number of Sheets after %d-up and %s mode: %d\n" +
            "Cost per Sheet: %.2f\n" +
            "Total Paper Cost: %.2f\n" +
            "Final Cost(Ink + Paper): %d\n\n"+
            "Markup: %.2f%%\n" +
            "Minimum Fee: %.2f\n" +
            "Finishing Cost: %.2f\n" +
            "Discount: %.2f%%\n\n" +
            "Cost per document page: %.2f\n" +
            "Customer- Final Price: %d\n" +
            "Shop Profit: %d",
            jobId,report.fileName, report.fileSizeMB, report.totalPages, report.pdf_page_count,
            report.monoPagesCount, report.colorPagesCount,
            report.cyanUsage, report.magentaUsage, report.yellowUsage, report.blackUsage,
            report.processingTimeSeconds, report.whichMethod_Used, report.totalInkCost,report.N_up, report.inkCostAfterNup,
            report.N_up, report.duplexMode,
            report.numberOfPagesAfterNup, report.costOfASingle_PaperSheet, report.totalPaperCost, report.finalCost,
            report.markupPercentage, report.minimumFee, report.finishingCost, report.discountPercentage, report.CostPerPage, report.customerFinalPrice, report.Shop_profit);

            
         
        
    
    return ResponseEntity.ok(summaryMessage);
}

@GetMapping("/download_pdf/{jobId}")
public ResponseEntity<?> downloadPdfReport(@PathVariable String jobId) {
    // Retrieve the active calculation report[cite: 1]
    CalculationReport report = reports.getIfPresent(jobId); 
    if (report == null) {
        return ResponseEntity.notFound().build();
    }

    try {
        // 1. Generate the PDF byte arrays using your existing methods[cite: 1]
        byte[] shopPdf = generateShopReport(report); 
        byte[] customerPdf = generateCustomerReport(report); 

        // 2. Format a safe file name based on your existing logic[cite: 1]
        String safeFileName = (report.fileName != null ? Paths.get(report.fileName).getFileName().toString() : "Document").replaceAll("[^a-zA-Z0-9.-]", "_"); 

        // 3. Define the new dedicated folder path in the user's Downloads directory
        Path downloadsDir = Paths.get(System.getProperty("user.home"), "Downloads");
        String folderName = "PRINT_ESTI_" + safeFileName + "_" + jobId;
        Path jobFolder = downloadsDir.resolve(folderName);
        
        // Create the directory if it does not already exist
        Files.createDirectories(jobFolder);

        // 4. Resolve file paths inside the newly created folder
        Path shopPdfPath = jobFolder.resolve("SHOP_RPT_" + safeFileName + ".pdf");
        Path customerPdfPath = jobFolder.resolve("CUST_RPT_" + safeFileName + ".pdf");

        // 5. Write files directly to the local disk
        Files.write(shopPdfPath, shopPdf);
        Files.write(customerPdfPath, customerPdf);

        // 6. Automatically open the saved files on the user's screen
        // 6. Reliably open the folder using modern ProcessBuilder (Java 18+)
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Natively trigger Windows Explorer to open the specific directory safely
                new ProcessBuilder("explorer.exe", jobFolder.toString()).start();
            } else if (Desktop.isDesktopSupported()) {
                // Fallback for Mac/Linux environments
                Desktop.getDesktop().open(jobFolder.toFile());
            }
        } catch (Exception ex) {
            System.err.println("Could not open directory: " + ex.getMessage());
        }

        // 7. Return a simple success JSON instead of a ZIP file attachment
        return ResponseEntity.ok(Map.of(
            "message", "Reports saved to new folder and opened successfully.",
            "folderPath", jobFolder.toString()
        ));

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
}

private byte[] generateShopReport(CalculationReport report) throws Exception {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                Color COLOR_PRIMARY = new Color(30, 41, 59);     
                Color COLOR_SECONDARY = new Color(71, 85, 105);  
                Color COLOR_CARD_BG = new Color(248, 250, 252);  
                Color COLOR_CARD_BORDER = new Color(226, 232, 240); 
                Color COLOR_TOTAL_BG = new Color(240, 253, 244);  
                Color COLOR_TOTAL_BORDER = new Color(34, 197, 94); 
                Color COLOR_TOTAL_TEXT = new Color(21, 128, 61);   

                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontReg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                Path logoStorageDir = Paths.get(System.getProperty("user.home"), ".ink_estimator", "logo");
                Path pngPath = logoStorageDir.resolve("shop-logo.png");
                Path jpgPath = logoStorageDir.resolve("shop-logo.jpg");
                
                PDImageXObject pdImage = null;

                if (Files.exists(pngPath)) {
                    pdImage = PDImageXObject.createFromFileByContent(pngPath.toFile(), doc);
                } else if (Files.exists(jpgPath)) {
                    pdImage = PDImageXObject.createFromFileByContent(jpgPath.toFile(), doc);
                } else {
                    ClassPathResource defaultLogo = new ClassPathResource("static/default-logo.png");
                    if (defaultLogo.exists()) {
                        byte[] logoBytes = StreamUtils.copyToByteArray(defaultLogo.getInputStream());
                        pdImage = PDImageXObject.createFromByteArray(doc, logoBytes, "default-logo");
                    }
                }

                if (pdImage != null) {
                    float maxWidth = 130f;
                    float maxHeight = 45f;
                    float imgWidth = pdImage.getWidth();
                    float imgHeight = pdImage.getHeight();

                    float scale = Math.min(maxWidth / imgWidth, maxHeight / imgHeight);
                    float drawWidth = imgWidth * scale;
                    float drawHeight = imgHeight * scale;

                    float xPos = 595f - 40f - drawWidth; 
                    float yPos = 842f - 40f - drawHeight; 

                    content.drawImage(pdImage, xPos, yPos, drawWidth, drawHeight);
                }

                content.beginText();
                content.setFont(fontBold, 18);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 770);
                content.showText("Cost Estimation Report");
                content.endText();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setNonStrokingColor(COLOR_SECONDARY);
                content.newLineAtOffset(40, 755);
                content.showText("PDF Ink & Paper Analysis Breakdown");
                content.endText();

                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.moveTo(40, 742);
                content.lineTo(555, 742);
                content.stroke();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 625, 515, 105);
                content.fillAndStroke();

                String rawFileName = (report.fileName != null) ? Paths.get(report.fileName).getFileName().toString() : "Unknown File";
                List<String> fileNameLines = wrapText("File Name: " + rawFileName, fontReg, 9f, 240f);

                content.beginText();
                content.setFont(fontReg, 9);
                content.setLeading(13f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 715);
                content.showText("Job ID: " + report.jobId); content.newLine();
                
                int fnCount = 0;
                for (String fnLine : fileNameLines) {
                    if (fnCount >= 2) {
                        content.showText("...");
                        content.newLine();
                        break;
                    }
                    content.showText(fnLine); content.newLine();
                    fnCount++;
                }
                
                content.showText(String.format("File Size: %.2f MB", report.fileSizeMB));
                content.endText();

                String monoStr = "Mono Pages: " + formatPageRanges(report.monoPages);
                String colorStr = "Color Pages: " + formatPageRanges(report.colorPages);

                List<String> monoLines = wrapText(monoStr, fontReg, 9f, 230f);
                List<String> colorLines = wrapText(colorStr, fontReg, 9f, 230f);

                content.beginText();
                content.setFont(fontReg, 9);
                content.setLeading(13f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(310, 715);

                int mCount = 0;
                for (String line : monoLines) {
                    if (mCount >= 2) {
                        content.showText(line + "...");
                        content.newLine();
                        break;
                    }
                    content.showText(line); content.newLine();
                    mCount++;
                }

                int cCount = 0;
                for (String line : colorLines) {
                    if (cCount >= 2) {
                        content.showText(line + "...");
                        content.newLine();
                        break;
                    }
                    content.showText(line); content.newLine();
                    cCount++;
                }

                content.showText(String.format("Pages Analyzed: %d (Mono: %d, Color: %d)",
                        report.totalPages, report.monoPagesCount, report.colorPagesCount)); content.newLine();
                content.showText("Processing Time: " + report.processingTimeSeconds + " seconds");
                content.endText();

                content.beginText();
                content.setFont(fontBold, 12);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 605);
                content.showText("Ink Analysis (Original Document)");
                content.endText();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 505, 515, 85);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(16f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 572);
                content.showText(String.format("Cyan Coverage:     %.2f%%   (Cost: %.2f)", report.cyanUsage, report.cyanCost)); content.newLine();
                content.showText(String.format("Magenta Coverage:  %.2f%%   (Cost: %.2f)", report.magentaUsage, report.magentaCost)); content.newLine();
                content.showText(String.format("Yellow Coverage:   %.2f%%   (Cost: %.2f)", report.yellowUsage, report.yellowCost)); content.newLine();
                content.showText(String.format("Black Coverage:    %.2f%%   (Cost: %.2f)", report.blackUsage, report.blackCost));
                content.endText();

                content.beginText();
                content.setFont(fontBold, 12);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 485);
                content.showText("Configuration & Ink Summary");
                content.endText();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 395, 515, 75);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(16f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 452);
                content.showText(String.format("Target Paper: %s   |   Layout (N-up): %s   |   Duplex Mode: %s",
                        report.targetPaperSize, report.printLayout, report.duplexMode)); content.newLine();
                content.showText(String.format("Ink Cost (Scaled): %.2f", report.totalInkCost)); content.newLine();
                content.showText(String.format("Ink Cost (After N-Up): %.2f", report.inkCostAfterNup));
                content.endText();

                content.beginText();
                content.setFont(fontBold, 12);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 375);
                content.showText("Paper Costs");
                content.endText();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 285, 515, 75);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(16f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 342);
                content.showText(String.format("Physical Sheets Required: %d", report.numberOfPagesAfterNup)); content.newLine();
                content.showText(String.format("Cost per Sheet: %.2f", report.costOfASingle_PaperSheet)); content.newLine();
                content.showText(String.format("Total Paper Cost: %.2f", report.totalPaperCost));content.newLine();
                content.showText(String.format("Tiered multiplier: %.2f", report.tieredMultiplier));
                content.endText();

               content.beginText();
                content.setFont(fontBold, 12);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 265);
                content.showText("Profit Summary");
                content.endText();

                // 1. Details Box (Placed below the title)
                // Bottom-left Y is 180. Height is 70. Top edge is 250 (leaves 15pt gap below title)
                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 180, 515, 70);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(16f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 235); // 20pt padding from top of the box
                content.showText(String.format("Markup Percentage: %.2f%%", report.markupPercentage)); content.newLine();
                content.showText(String.format("Minimum Fee: %.2f", report.minimumFee)); content.newLine();
                content.showText(String.format("Finishing Cost: %.2f", report.finishingCost)); content.newLine();
                content.showText(String.format("Discount Percentage: %.2f%%", report.discountPercentage));
                content.endText();

                float boxY = 100f;
                float boxH = 65f;
                float titleY = boxY + 40f;
                float valueY = boxY + 18f;

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.addRect(40, boxY, 165, boxH);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontBold, 10);
                content.setNonStrokingColor(COLOR_SECONDARY);
                content.newLineAtOffset(55, titleY);
                content.showText("TOTAL SHOP COST");
                content.endText();

                content.beginText();
                content.setFont(fontBold, 16);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, valueY);
                content.showText(String.format("%.2f", (double) report.finalCost));
                content.endText();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.addRect(215, boxY, 165, boxH);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontBold, 10);
                content.setNonStrokingColor(COLOR_SECONDARY);
                content.newLineAtOffset(230, titleY);
                content.showText("CUSTOMER CHARGED");
                content.endText();

                content.beginText();
                content.setFont(fontBold, 16);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(230, valueY);
                content.showText(String.format("%.2f", (double) report.customerFinalPrice));
                content.endText();

                content.setNonStrokingColor(COLOR_TOTAL_BG);
                content.setStrokingColor(COLOR_TOTAL_BORDER);
                content.setLineWidth(1.5f);
                content.addRect(390, boxY, 165, boxH);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontBold, 10);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(405, titleY);
                content.showText("NET PROFIT");
                content.endText();

                content.beginText();
                content.setFont(fontBold, 16);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(405, valueY);
                content.showText(String.format("%.2f", (double) report.Shop_profit));
                content.endText();
            }
        
        doc.save(baos);
        return baos.toByteArray();
    }
}

private byte[] generateCustomerReport(CalculationReport report) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontReg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                Color COLOR_PRIMARY = new Color(30, 41, 59);
                Color COLOR_SECONDARY = new Color(71, 85, 105);
                Color COLOR_CARD_BG = new Color(248, 250, 252);
                Color COLOR_CARD_BORDER = new Color(226, 232, 240);
                Color COLOR_TOTAL_BG = new Color(240, 253, 244);
                Color COLOR_TOTAL_BORDER = new Color(34, 197, 94);
                Color COLOR_TOTAL_TEXT = new Color(21, 128, 61);

                Path logoStorageDir = Paths.get(System.getProperty("user.home"), ".ink_estimator", "logo");
                Path pngPath = logoStorageDir.resolve("shop-logo.png");
                Path jpgPath = logoStorageDir.resolve("shop-logo.jpg");
                
                PDImageXObject pdImage = null;

                if (Files.exists(pngPath)) {
                    pdImage = PDImageXObject.createFromFileByContent(pngPath.toFile(), doc);
                } else if (Files.exists(jpgPath)) {
                    pdImage = PDImageXObject.createFromFileByContent(jpgPath.toFile(), doc);
                } else {
                    ClassPathResource defaultLogo = new ClassPathResource("static/default-logo.png");
                    if (defaultLogo.exists()) {
                        byte[] logoBytes = StreamUtils.copyToByteArray(defaultLogo.getInputStream());
                        pdImage = PDImageXObject.createFromByteArray(doc, logoBytes, "default-logo");
                    }
                }

                if (pdImage != null) {
                    float maxWidth = 130f;
                    float maxHeight = 45f;
                    float imgWidth = pdImage.getWidth();
                    float imgHeight = pdImage.getHeight();

                    float scale = Math.min(maxWidth / imgWidth, maxHeight / imgHeight);
                    float drawWidth = imgWidth * scale;
                    float drawHeight = imgHeight * scale;

                    float xPos = 595f - 40f - drawWidth; 
                    float yPos = 842f - 40f - drawHeight; 

                    content.drawImage(pdImage, xPos, yPos, drawWidth, drawHeight);
                }


                // --- INJECT SHOP INFORMATION HERE ---
                content.beginText();
                content.setFont(fontBold, 11);
                content.setNonStrokingColor(COLOR_PRIMARY);
                // Positioned on the right side, aligning horizontally with the main title
                content.newLineAtOffset(350, 785); 
                content.showText(report.shopName != null && !report.shopName.isEmpty() ? report.shopName : "Shop Information");
                
                content.setFont(fontReg, 10);
                content.setNonStrokingColor(COLOR_SECONDARY);
                content.newLineAtOffset(0, -14); // Move down a line for the address
                content.showText(report.shopAddress != null ? report.shopAddress : "");
                
                content.newLineAtOffset(0, -14); // Move down a line for the contact
                content.showText(report.shopContact != null ? report.shopContact : "");
                content.endText();
                // ------------------------------------


                content.beginText();
                content.setFont(fontBold, 18);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 770);
                content.showText("Print Job Estimate");
                content.endText();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setNonStrokingColor(COLOR_SECONDARY);
                content.newLineAtOffset(40, 755);
                content.showText("Customer Quotation & Job Details");
                content.endText();

                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.moveTo(40, 742);
                content.lineTo(555, 742);
                content.stroke();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 615, 515, 110);
                content.fillAndStroke();

                String rawFileName = (report.fileName != null) ? Paths.get(report.fileName).getFileName().toString() : "Unknown File";
                List<String> fileNameLines = wrapText("File Name: " + rawFileName, fontReg, 10f, 240f);

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(14f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 705);
                content.showText("Job ID: " + report.jobId); content.newLine();
                
                int fnCount = 0;
                for (String fnLine : fileNameLines) {
                    if (fnCount >= 2) {
                        content.showText("...");
                        content.newLine();
                        break;
                    }
                    content.showText(fnLine); content.newLine();
                    fnCount++;
                }
                
                content.showText("Target Paper Size: " + report.targetPaperSize); content.newLine();
                content.showText("Print Layout (N-up): " + report.printLayout); content.newLine();
                content.showText("Duplex Mode: " + report.duplexMode); content.newLine();
                //content.showText("Total Document Pages: " + report.totalPages);
                content.endText();

                String monoStr = "Mono Pages: " + formatPageRanges(report.monoPages);
                String colorStr = "Color Pages: " + formatPageRanges(report.colorPages);

                List<String> monoLines = wrapText(monoStr, fontReg, 10f, 230f);
                List<String> colorLines = wrapText(colorStr, fontReg, 10f, 230f);

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(14f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(310, 705);

                int mCount = 0;
                for (String line : monoLines) {
                    if (mCount >= 2) {
                        content.showText(line + "...");
                        content.newLine();
                        break;
                    }
                    content.showText(line); content.newLine();
                    mCount++;
                }

                int cCount = 0;
                for (String line : colorLines) {
                    if (cCount >= 2) {
                        content.showText(line + "...");
                        content.newLine();
                        break;
                    }
                    content.showText(line); content.newLine();
                    cCount++;
                }

                content.showText(String.format("Pages Analyzed: %d (Mono: %d, Color: %d)",
                        report.totalPages, report.monoPagesCount, report.colorPagesCount)); 
                content.endText();

                content.beginText();
                content.setFont(fontBold, 12);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(40, 580);
                content.showText("Order Summary");
                content.endText();

                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 480, 515, 85);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontReg, 10);
                content.setLeading(16f);
                content.setNonStrokingColor(COLOR_PRIMARY);
                content.newLineAtOffset(55, 545);
                
                content.showText("Finishing Services: " + (report.finishingCost > 0 ? String.format("%.2f", report.finishingCost) : "None")); content.newLine();
                content.showText("Discount Applied: " + (report.discountPercentage > 0 ? String.format("%.1f%%", report.discountPercentage) : "0.0%")); content.newLine();
                if (report.minimumFee > 0) {
                    content.showText(String.format("Minimum Order Fee Threshold: %.2f", report.minimumFee));content.newLine();
                } else {
                    content.showText("Standard Pricing Tier Applied");content.newLine();
                }
                content.showText(String.format("Cost per document page: %.2f", report.CostPerPage));
                content.endText();

                content.setNonStrokingColor(COLOR_TOTAL_BG);
                content.setStrokingColor(COLOR_TOTAL_BORDER);
                content.setLineWidth(1.5f);
                content.addRect(40, 390, 515, 60);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontBold, 11);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(60, 425);
                content.showText("TOTAL COST");
                content.endText();

                content.beginText();
                content.setFont(fontReg, 18);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(60, 403);
                content.showText(String.format("%.2f", (double) report.customerFinalPrice));
                content.endText();

                content.beginText();
                // If you have a regular font defined (like fontRegular), use it here. 
                // Otherwise, you can keep using fontBold or PDType1Font.HELVETICA
                content.setFont(fontBold, 8); 
                content.setNonStrokingColor(java.awt.Color.GRAY); 
                
                // Positioned at x=40 to align with the left side of your Total box, and y=40 near the bottom of the page
                content.newLineAtOffset(40, 80); 
                content.showText("Software developed by Inuwara Design & Integration (InuwaraDI.com)");content.newLine();
                content.showText("For inquiries, email: contact@inuwaradi.com");content.newLine();
                content.showText("For projects, please visit: web: https://inuwaradi.com");content.newLine();
                content.showText("or simply give a phone call: +94 76 590 9990");content.newLine();  
                content.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private String formatPageRanges(List<Integer> pages) {
        if (pages == null || pages.isEmpty()) return "None";
        List<Integer> sorted = new ArrayList<>(pages);
        Collections.sort(sorted);

        StringBuilder sb = new StringBuilder();
        int start = sorted.get(0);
        int prev = start;

        for (int i = 1; i < sorted.size(); i++) {
            int current = sorted.get(i);
            if (current == prev + 1) {
                prev = current;
            } else {
                if (start == prev) {
                    sb.append(start).append(", ");
                } else {
                    sb.append(start).append("-").append(prev).append(", ");
                }
                start = current;
                prev = current;
            }
        }
        if (start == prev) {
            sb.append(start);
        } else {
            sb.append(start).append("-").append(prev);
        }
        return sb.toString();
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return lines;

        try {
            String[] words = text.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = (currentLine.length() == 0) ? word : currentLine + " " + word;
                float textWidth = font.getStringWidth(testLine) / 1000f * fontSize;

                if (textWidth > maxWidth) {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        lines.add(testLine);
                        currentLine = new StringBuilder();
                    }
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        } catch (Exception e) {
            lines.add(text);
        }

        return lines;
    }

    public List<List<Integer>> parsePrintJobs(String monoInput, String colorInput, int totalPages) {
        List<List<Integer>> result = new ArrayList<>();
        boolean isMonoEmpty = (monoInput == null || monoInput.trim().isEmpty());
        boolean isColorEmpty = (colorInput == null || colorInput.trim().isEmpty());

        if (isMonoEmpty && isColorEmpty) {
            List<Integer> fullMonoList = new ArrayList<>();
            for (int i = 1; i <= totalPages; i++) fullMonoList.add(i);
            result.add(fullMonoList);
            result.add(new ArrayList<>());
            return result;
        }

        Set<Integer> colorPages = parseSingleString(colorInput, totalPages);
        Set<Integer> monoPages = parseSingleString(monoInput, totalPages);
        monoPages.removeAll(colorPages);

        List<Integer> finalMonoList = new ArrayList<>(monoPages);
        Collections.sort(finalMonoList);
        List<Integer> finalColorList = new ArrayList<>(colorPages);
        Collections.sort(finalColorList);

        result.add(finalMonoList);
        result.add(finalColorList);
        return result;
    }

    private Set<Integer> parseSingleString(String input, int totalPages) {
        Set<Integer> pages = new HashSet<>();
        if (input == null || input.trim().isEmpty()) return pages;

        for (String part : input.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                if (part.endsWith("-")) {
                    try {
                        int start = Integer.parseInt(part.substring(0, part.length() - 1).trim());
                        for (int i = start; i <= totalPages; i++) pages.add(i);
                    } catch (NumberFormatException ignored) {}
                } else {
                    String[] range = part.split("-");
                    if (range.length == 2) {
                        try {
                            int start = Integer.parseInt(range[0].trim());
                            int end = Math.min(Integer.parseInt(range[1].trim()), totalPages);
                            for (int i = start; i <= end; i++) pages.add(i);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                try {
                    int page = Integer.parseInt(part);
                    if (page <= totalPages) pages.add(page);
                } catch (NumberFormatException ignored) {}
            }
        }
        return pages;
    }
}