package com.inuwara.pdf_ink_cost_estimator.model;

import jakarta.persistence.*;

@Entity
@Table(name = "printer_settings")
public class printerSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- General Settings ---
    // Maps to inkjet or laser selection
    @Column(name = "printer_type", nullable = false)
    private String printerType;

    @Column(name = "user_id", nullable = true)
    private String userId; 

    // Maps to ISO coverage input (default 5)[cite: 3]
    @Column(name = "iso_coverage_percentage", nullable = false)
    private Double isoCoveragePercentage = 5.0;

    // Maps to Full Color (CMYK) or Monochrome selection[cite: 3]
    @Column(name = "color_capability", nullable = false)
    private String colorCapability;


    // --- Black Cartridge ---
    // Maps to monochrome price and yield inputs[cite: 3]
    @Column(name = "black_price", nullable = false)
    private Double blackPrice;

    @Column(name = "black_yield", nullable = false)
    private Integer blackYield;


    // --- Color Cartridges ---
    // Maps to Cyan price and yield inputs[cite: 3]
    @Column(name = "cyan_price")
    private Double cyanPrice;

    @Column(name = "cyan_yield")
    private Integer cyanYield;

    // Maps to Magenta price and yield inputs[cite: 3]
    @Column(name = "magenta_price")
    private Double magentaPrice;

    @Column(name = "magenta_yield")
    private Integer magentaYield;

    // Maps to Yellow price and yield inputs[cite: 3]
    @Column(name = "yellow_price")
    private Double yellowPrice;

    @Column(name = "yellow_yield")
    private Integer yellowYield;


    // --- Print Job Preferences ---
    // Maps to printing in black & white or full color selection[cite: 3]
    @Column(name = "print_job_color_type", nullable = false)
    private String printJobColorType;

    // Maps to cost per paper input[cite: 3]
    @Column(name = "paper_cost", nullable = false)
    private Double paperCost;

    // --- Getters and Setters ---
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPrinterType() { return printerType; }
    public void setPrinterType(String printerType) { this.printerType = printerType; }

    public Double getIsoCoveragePercentage() { return isoCoveragePercentage; }
    public void setIsoCoveragePercentage(Double isoCoveragePercentage) { this.isoCoveragePercentage = isoCoveragePercentage; }

    public String getColorCapability() { return colorCapability; }
    public void setColorCapability(String colorCapability) { this.colorCapability = colorCapability; }

    public Double getBlackPrice() { return blackPrice; }
    public void setBlackPrice(Double blackPrice) { this.blackPrice = blackPrice; }

    public Integer getBlackYield() { return blackYield; }
    public void setBlackYield(Integer blackYield) { this.blackYield = blackYield; }

    public Double getCyanPrice() { return cyanPrice; }
    public void setCyanPrice(Double cyanPrice) { this.cyanPrice = cyanPrice; }

    public Integer getCyanYield() { return cyanYield; }
    public void setCyanYield(Integer cyanYield) { this.cyanYield = cyanYield; }

    public Double getMagentaPrice() { return magentaPrice; }
    public void setMagentaPrice(Double magentaPrice) { this.magentaPrice = magentaPrice; }

    public Integer getMagentaYield() { return magentaYield; }
    public void setMagentaYield(Integer magentaYield) { this.magentaYield = magentaYield; }

    public Double getYellowPrice() { return yellowPrice; }
    public void setYellowPrice(Double yellowPrice) { this.yellowPrice = yellowPrice; }

    public Integer getYellowYield() { return yellowYield; }
    public void setYellowYield(Integer yellowYield) { this.yellowYield = yellowYield; }

    public String getPrintJobColorType() { return printJobColorType; }
    public void setPrintJobColorType(String printJobColorType) { this.printJobColorType = printJobColorType; }

    public Double getPaperCost() { return paperCost; }
    public void setPaperCost(Double paperCost) { this.paperCost = paperCost; }
}