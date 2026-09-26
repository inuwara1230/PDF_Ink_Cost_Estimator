package com.inuwara.pdf_ink_cost_estimator.model;

import jakarta.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "printer_settings")
public class printerSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- General Settings ---
    @Column(name = "printer_type", nullable = false)
    private String printerType;

    @Column(name = "user_id", nullable = true)
    private String userId; 

    @Column(name = "iso_coverage_percentage", nullable = false)
    private Double isoCoveragePercentage = 5.0;

    @Column(name = "color_capability", nullable = false)
    private String colorCapability;

    // --- Black Cartridge ---
    @Column(name = "black_price", nullable = false)
    private Double blackPrice;

    @Column(name = "black_yield", nullable = false)
    private Integer blackYield;

    // --- Color Cartridges ---
    @Column(name = "cyan_price")
    private Double cyanPrice;

    @Column(name = "cyan_yield")
    private Integer cyanYield;

    @Column(name = "magenta_price")
    private Double magentaPrice;

    @Column(name = "magenta_yield")
    private Integer magentaYield;

    @Column(name = "yellow_price")
    private Double yellowPrice;

    @Column(name = "yellow_yield")
    private Integer yellowYield;

    // --- Print Job Preferences ---
    @Column(name = "A4_cost", nullable = false)
    private Double a4Cost;

    @Column(name = "A3_cost", nullable = false)
    private Double a3Cost;

    @Column(name = "A5_cost", nullable = false)
    private Double a5Cost;

    @Column(name = "legal_cost", nullable = false)
    private Double legalCost;

    @Column(name = "markup_percentage", nullable = false)
    private Double markupPercentage = 100.0;

    @Column(name = "minimum_fee", nullable = false)
    private Double minimumFee = 0.0;

    @Column(name = "finishing_cost", nullable = false)
    private Double finishingCost = 0.0;

    @Column(name= "shop_name", nullable = true) // Optional field for shop name
    private String shopName;

    @Column (name= "shop_address", nullable = true) // Optional field for shop address
    private String shopAddress;

    @Column (name= "shop_contact", nullable = true) // Optional field for shop contact(phone/email)
    private String shopContact;

    // --- Custom Dynamic Papers ---
    @Embeddable
    public static class CustomPaper {
        private String name;
        private Double areaInPoints;
        private Double price;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Double getAreaInPoints() { return areaInPoints; }
        public void setAreaInPoints(Double areaInPoints) { this.areaInPoints = areaInPoints; }
        
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }

    @ElementCollection
    @CollectionTable(name = "custom_papers", joinColumns = @JoinColumn(name = "printer_settings_id"))
    private List<CustomPaper> customPapers = new ArrayList<>();

    // --- Getters and Setters ---

    public List<CustomPaper> getCustomPapers() { return customPapers; }
    public void setCustomPapers(List<CustomPaper> customPapers) { this.customPapers = customPapers; }

    public Double getMarkupPercentage() { return markupPercentage; }
    public void setMarkupPercentage(Double markupPercentage) { this.markupPercentage = markupPercentage; }

    public Double getMinimumFee() { return minimumFee; }
    public void setMinimumFee(Double minimumFee) { this.minimumFee = minimumFee; }

    public Double getFinishingCost() { return finishingCost; }
    public void setFinishingCost(Double finishingCost) { this.finishingCost = finishingCost; }
    
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

    public Double getA4Cost() { return a4Cost; }
    public void setA4Cost(Double a4Cost) { this.a4Cost = a4Cost; }

    public Double getA3Cost() { return a3Cost; }
    public void setA3Cost(Double a3Cost) { this.a3Cost = a3Cost; }

    public Double getA5Cost() { return a5Cost; }
    public void setA5Cost(Double a5Cost) { this.a5Cost = a5Cost; }

    public Double getLegalCost() { return legalCost; }
    public void setLegalCost(Double legalCost) { this.legalCost = legalCost; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getShopAddress() { return shopAddress; }
    public void setShopAddress(String shopAddress) { this.shopAddress = shopAddress; }

    public String getShopContact() { return shopContact; }
    public void setShopContact(String shopContact) { this.shopContact = shopContact; }
}