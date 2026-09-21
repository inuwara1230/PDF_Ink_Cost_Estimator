package com.inuwara.pdf_ink_cost_estimator;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.Color;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    // Store finished calculation reports mapped by jobId
    private final Map<String, CalculationReport> reports = new ConcurrentHashMap<>();

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
        // @RequestParam(value = "colorCapability", required = false, defaultValue = "color") String colorCapability,
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
        @RequestParam(value = "monochromePageRange", required = false, defaultValue = "") String monochromePageRange,
        @RequestParam (value = "markupPercentage", required = false, defaultValue = "100") double markupPercentage,
        @RequestParam (value = "minimumFee", required = false, defaultValue = "0") double minimumFee,
        @RequestParam (value = "finishingCost", required = false, defaultValue = "0") double finishingCost,
        @RequestParam (value = "discountPercentage", required = false, defaultValue = "0") double discountPercentage)
    {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty.");
        if (!"application/pdf".equals(file.getContentType())) return ResponseEntity.badRequest().body("Only PDF files are allowed.");

        try {
            double fileSizeInBytes = file.getSize();
            String fileName = file.getOriginalFilename();
            byte[] fileBytes = file.getBytes();
            // Ignore the color capability for now
            new Thread(() -> processPDFinBackground(fileBytes, fileName, jobID,
                 fileSizeInBytes, blackPrice, blackYield, isoCoverage, cyanPrice, cyanYield,
                  magentaPrice, magentaYield, yellowPrice, yellowYield, a4Cost, a3Cost, a5Cost, legalCost,
                targetPaperSize, printLayout, duplexMode, colorPageRange, monochromePageRange, markupPercentage, minimumFee, finishingCost, discountPercentage)).start();
            return ResponseEntity.ok("Upload received, processing started...");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to read file bytes.");
        }
    }

    private void processPDFinBackground(byte[] fileBytes, String fileName, 
        String jobId, double fileSizeInBytes, double blackPrice, double blackYield,
        double isoCoverage, double cyanPrice, double cyanYield, double magentaPrice, 
        double magentaYield, double yellowPrice, double yellowYield, double a4Cost, double a3Cost, double a5Cost, double legalCost,
        String targetPaperSize, String printLayout, String duplexMode, String colorPageRange, String monochromePageRange,
        double markupPercentage, double minimumFee, double finishingCost, double discountPercentage) {

        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;

        try (PDDocument document = Loader.loadPDF(fileBytes)) {
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

                image.flush();
                if (isColor) {
                    totalCyan += (pageCyan / totalPixels) * 100 * totalAreaMultiplier;
                    totalMagenta += (pageMagenta / totalPixels) * 100 * totalAreaMultiplier;
                    totalYellow += (pageYellow / totalPixels) * 100 * totalAreaMultiplier;
                }
                totalBlack += (pageBlack / totalPixels) * 100 * totalAreaMultiplier;
            }

            double final_pdf_page_area = accumulated_pdf_page_area / total_pdf_pages;
            double targetPaperArea = switch (targetPaperSize) {
                case "A3" -> 1002270.0;
                case "A5" -> 250567.0;
                case "Legal" -> 604800.0;
                default -> 501135.0;
            };
            double ink_cost_multiplier = targetPaperArea / final_pdf_page_area;

            double cyanCost = totalCyan * ink_cost_multiplier * (cyanPrice / (cyanYield * isoCoverage)); 
            double magentaCost = totalMagenta * ink_cost_multiplier * (magentaPrice / (magentaYield * isoCoverage));
            double yellowCost = totalYellow * ink_cost_multiplier * (yellowPrice / (yellowYield * isoCoverage));
            double blackCost = totalBlack * ink_cost_multiplier * (blackPrice / (blackYield * isoCoverage));

            double totalInkCost = cyanCost + magentaCost + yellowCost + blackCost;

            int N_up = switch (printLayout) {
                case "booklet", "2up" -> 2;
                case "4up" -> 4;
                default -> 1;
            };
            double ink_cost_after_N_up = totalInkCost / N_up;

            int duplexFactor = "duplex".equalsIgnoreCase(duplexMode) ? 2 : 1;
            int numberOfpagesAfterNup = (int) Math.ceil((double) allPages.size() / (N_up * duplexFactor));

            double totalPaperCost = switch (targetPaperSize) {
                case "A3" -> numberOfpagesAfterNup * a3Cost;
                case "A5" -> numberOfpagesAfterNup * a5Cost;
                case "Legal" -> numberOfpagesAfterNup * legalCost;
                default -> numberOfpagesAfterNup * a4Cost;
            };

            Double costOfASingle_PaperSheet = switch (targetPaperSize) {
                case "A3" -> a3Cost;
                case "A5" -> a5Cost;
                case "Legal" -> legalCost;
                default -> a4Cost;
            }; 

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
            double markedUpCost = finalCost * (1+(markupPercentage / 100.0)*tiered_Multiplier);
            double subtotal = markedUpCost + finishingCost;

            if (subtotal < minimumFee) {
                subtotal = minimumFee;
            }

            Double customerPrice_before_ceiling = subtotal - (subtotal * (discountPercentage / 100.0));

            System.out.println("Subtotal before discount: " + customerPrice_before_ceiling);

            int customerFinalPrice = (int) Math.ceil(customerPrice_before_ceiling);

            int Shop_profit = customerFinalPrice - finalCost;

            // Store calculation output in memory for PDF generation
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

            reports.put(jobId, report);

            // Send status messages to SSE UI
            String summaryMessage = String.format("File size: %.2f MB | Analyzed: %d/%d\n" +
                    "Mono Pages: %d | Color Pages: %d\n" +
                    "Ink Usage - C: %.2f%%, M: %.2f%%, Y: %.2f%%, K: %.2f%%\n" +
                    "Processing Time: %d s\n\n" +
                    "Ink Cost after scalling: %.2f\n" +
                    "Ink Cost after N-up: %.2f\n\n" +
                    "number of Sheets after N-up: %d\n" +
                    "Cost per Sheet: %.2f\n" +
                    "Total Paper Cost: %.2f\n" +
                    "Tiered Multiplier: %.2f\n" +
                    "Final Cost(Ink + Paper): %d\n\n"+
                    "Markup: %.2f%%\n" +
                    "Minimum Fee: %.2f\n" +
                    "Finishing Cost: %.2f\n" +
                    "Discount: %.2f%%\n\n" +
                    "Customer Final Price: %d\n" +
                    "Shop Profit: %d",
                    fileSizeInMB, allPages.size(), totalPages,
                    monoPages.size(), colorPages.size(),
                    totalCyan, totalMagenta, totalYellow, totalBlack,
                    processingTime, totalInkCost, ink_cost_after_N_up,
                    numberOfpagesAfterNup, costOfASingle_PaperSheet, totalPaperCost, tiered_Multiplier,finalCost,
                     markupPercentage, minimumFee, finishingCost, discountPercentage, customerFinalPrice, Shop_profit);

            emitter.send(summaryMessage);

            // Send named SSE completion event and close emitter
            emitter.send(SseEmitter.event().name("COMPLETE").data("Analysis complete!"));
            emitter.complete();

        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }



@GetMapping("/download_pdf/{jobId}")
public ResponseEntity<byte[]> downloadPdfReport(@PathVariable String jobId) {
    CalculationReport report = reports.get(jobId);
    if (report == null) {
        return ResponseEntity.notFound().build();
    }

    try (ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
         ZipOutputStream zos = new ZipOutputStream(zipBaos)) {

        // 1. Generate and add Shop Report to ZIP
        byte[] shopPdf = generateShopReport(report);
        zos.putNextEntry(new ZipEntry("SHOP_RPT_"+report.fileName + jobId + ".pdf"));
        zos.write(shopPdf);
        zos.closeEntry();

        // 2. Generate and add Customer Report to ZIP
        byte[] customerPdf = generateCustomerReport(report);
        zos.putNextEntry(new ZipEntry("CUST_RPT_"+report.fileName + jobId + ".pdf"));
        zos.write(customerPdf);
        zos.closeEntry();

        zos.finish();

        // 3. Send the ZIP file
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/zip"));
        
        String safeFileName = (report.fileName != null ? report.fileName : "Document").replaceAll("[^a-zA-Z0-9.-]", "_");
        headers.setContentDispositionFormData("attachment", "REPORTS" + "_" + safeFileName + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .body(zipBaos.toByteArray());

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError().build();
    }
}

private byte[] generateShopReport(CalculationReport report) throws Exception {
    try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream content = new PDPageContentStream(doc, page)) {

                // --- 1. COLOR PALETTE & FONTS ---
                Color COLOR_PRIMARY = new Color(30, 41, 59);     // Slate Dark Blue
                Color COLOR_SECONDARY = new Color(71, 85, 105);  // Slate Medium Gray
                Color COLOR_CARD_BG = new Color(248, 250, 252);  // Soft Card Fill
                Color COLOR_CARD_BORDER = new Color(226, 232, 240); // Card Border
                Color COLOR_TOTAL_BG = new Color(240, 253, 244);  // Light Emerald Background
                Color COLOR_TOTAL_BORDER = new Color(34, 197, 94); // Emerald Border
                Color COLOR_TOTAL_TEXT = new Color(21, 128, 61);   // Emerald Dark Text

                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontReg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // --- 2. LOGO RESOLUTION & PLACEMENT ---
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

                // --- 3. HEADER TITLE & SUBTITLE ---
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

                // Header Divider Line
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.moveTo(40, 742);
                content.lineTo(555, 742);
                content.stroke();

                // --- 4. METADATA CARD (HEIGHT INCREASED TO 105 FOR WRAPPED LISTS) ---
                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 625, 515, 105);
                content.fillAndStroke();

                // Left Column Details
                String rawFileName = (report.fileName != null) ? report.fileName : "Unknown File";
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

                // Right Column Details (Formatted Ranges & Wrapped Text)
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

                // --- 5. INK ANALYSIS CARD ---
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

                // --- 6. CONFIGURATION & INK SUMMARY CARD ---
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

                // --- 7. PAPER COSTS CARD ---
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

                

              

                // --- 8. FINANCIAL SUMMARY (3-COLUMN DASHBOARD) ---
                float boxY = 195f;
                float boxH = 65f;
                float titleY = boxY + 40f;
                float valueY = boxY + 18f;

                // Column 1: Total Shop Cost (Base Ink + Paper)
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

                // Column 2: Customer Charged (After Markup/Discounts)
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

                // Column 3: Net Profit (Highlighted in Green)
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
        

        // NOTE: Paste your EXACT existing PDF drawing logic from your original downloadPdfReport here.
        // Everything from defining the colors to the doc.save() call.
        // Make sure it includes your logo loading, metadata card, ink analysis card, etc.
        
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

                // --- 1. COLOR PALETTE ---
                Color COLOR_PRIMARY = new Color(30, 41, 59);
                Color COLOR_SECONDARY = new Color(71, 85, 105);
                Color COLOR_CARD_BG = new Color(248, 250, 252);
                Color COLOR_CARD_BORDER = new Color(226, 232, 240);
                Color COLOR_TOTAL_BG = new Color(240, 253, 244);
                Color COLOR_TOTAL_BORDER = new Color(34, 197, 94);
                Color COLOR_TOTAL_TEXT = new Color(21, 128, 61);


                 // --- 2. LOGO RESOLUTION & PLACEMENT ---
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

                // --- 2. HEADER & SUBTITLE ---
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

                // Header Divider Line
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.setLineWidth(1.0f);
                content.moveTo(40, 742);
                content.lineTo(555, 742);
                content.stroke();

                // --- 3. METADATA CARD (JOB DETAILS) ---
                content.setNonStrokingColor(COLOR_CARD_BG);
                content.setStrokingColor(COLOR_CARD_BORDER);
                content.addRect(40, 615, 515, 110);
                content.fillAndStroke();

                // Left Column: Core File Info
                String rawFileName = (report.fileName != null) ? report.fileName : "Unknown File";
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
                content.showText("Total Document Pages: " + report.totalPages);
                content.endText();

                // Right Column: Page Range Breakdowns
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

                // --- 4. ORDER SUMMARY CARD ---
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
                    content.showText(String.format("Minimum Order Fee Threshold: %.2f", report.minimumFee));
                } else {
                    content.showText("Standard Pricing Tier Applied");
                }
                content.endText();

                // --- 5. GRAND TOTAL CALLOUT BOX ---
                content.setNonStrokingColor(COLOR_TOTAL_BG);
                content.setStrokingColor(COLOR_TOTAL_BORDER);
                content.setLineWidth(1.5f);
                content.addRect(40, 390, 515, 60);
                content.fillAndStroke();

                content.beginText();
                content.setFont(fontBold, 11);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(60, 425);
                content.showText("TOTAL ESTIMATED COST");
                content.endText();

                content.beginText();
                content.setFont(fontBold, 18);
                content.setNonStrokingColor(COLOR_TOTAL_TEXT);
                content.newLineAtOffset(60, 403);
                content.showText(String.format("%.2f", (double) report.customerFinalPrice));
                content.endText();
            }

            doc.save(baos);
            return baos.toByteArray();
        }
    }




    // Helper: Formats list of integers into range notation (e.g. 1-5, 8-10, 12)
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

    // Helper: Wraps text lines according to maximum pixel width
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