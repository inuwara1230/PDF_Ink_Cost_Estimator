package com.inuwara.pdf_ink_cost_estimator.controller;

import com.inuwara.pdf_ink_cost_estimator.model.printerSettings;
import com.inuwara.pdf_ink_cost_estimator.repository.PrinterSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.Optional;

@RestController
@RequestMapping("/api/settings")
public class PrinterSettingsController {

    private final PrinterSettingsRepository repository;

    public PrinterSettingsController(PrinterSettingsRepository repository) {
        this.repository = repository;
    }

    // 1. CREATE Endpoint (Strictly for saving new settings)
    @PostMapping("/create")
    public ResponseEntity<String> createSettings(
            @RequestBody printerSettings newSettings
            ) {
        
        
        
        // Check if settings already exist
        if (repository.count() > 0) {
            return ResponseEntity.status(400).body("Settings already exist for this user. Please use Update instead.");
        }

        try {
            newSettings.setUserId("default_user"); // Set a default user ID for now
            repository.save(newSettings);
            return ResponseEntity.ok("Settings created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to create settings.");
        }
    }

    // 2. UPDATE Endpoint (Strictly for modifying existing settings)
    @PutMapping("/update")
    public ResponseEntity<String> updateSettings(
            @RequestBody printerSettings updatedSettings
            ) {
        
        
        
        // Check if settings actually exist to update
        Optional<printerSettings> existingSettings = repository.findAll().stream().findFirst();
        if (existingSettings.isEmpty()) {
            return ResponseEntity.status(404).body("No saved settings found to update. Please Save first.");
        }

        try {
            printerSettings settingsToUpdate = existingSettings.get();
            
            settingsToUpdate.setPrinterType(updatedSettings.getPrinterType());
            settingsToUpdate.setIsoCoveragePercentage(updatedSettings.getIsoCoveragePercentage());
            settingsToUpdate.setColorCapability(updatedSettings.getColorCapability());
            settingsToUpdate.setBlackPrice(updatedSettings.getBlackPrice());
            settingsToUpdate.setBlackYield(updatedSettings.getBlackYield());
            settingsToUpdate.setCyanPrice(updatedSettings.getCyanPrice());
            settingsToUpdate.setCyanYield(updatedSettings.getCyanYield());
            settingsToUpdate.setMagentaPrice(updatedSettings.getMagentaPrice());
            settingsToUpdate.setMagentaYield(updatedSettings.getMagentaYield());
            settingsToUpdate.setYellowPrice(updatedSettings.getYellowPrice());
            settingsToUpdate.setYellowYield(updatedSettings.getYellowYield());
            settingsToUpdate.setPrintJobColorType(updatedSettings.getPrintJobColorType());
            
            settingsToUpdate.setA4Cost(updatedSettings.getA4Cost());
            settingsToUpdate.setA3Cost(updatedSettings.getA3Cost());
            settingsToUpdate.setA5Cost(updatedSettings.getA5Cost());
            settingsToUpdate.setLegalCost(updatedSettings.getLegalCost());

            repository.save(settingsToUpdate);
            return ResponseEntity.ok("Settings updated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to update settings.");
        }
    }

    @GetMapping("/get")
    public ResponseEntity<printerSettings> getSettings() { 
        
        
        return repository.findAll().stream().findFirst().map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/clear")
    public ResponseEntity<String> clearSettings() {
        
        try {
            if (repository.count() > 0) {
                repository.deleteAll();
                // 
                return ResponseEntity.ok("Settings successfully cleared from the database.");
            } else {
                return ResponseEntity.ok("No saved settings found to clear.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to clear settings from the database.");
        }
    }




}