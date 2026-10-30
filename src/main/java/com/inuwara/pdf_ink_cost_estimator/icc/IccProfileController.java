package com.inuwara.pdf_ink_cost_estimator.icc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/icc_profiles")
public class IccProfileController {

    private final IccProfileService profiles;

    public IccProfileController(IccProfileService profiles) {
        this.profiles = profiles;
    }

    /** Every profile the job screen can offer: built-in plus shop-added. */
    @GetMapping
    public List<String> list() {
        return profiles.allNames();
    }

    /** Only the profiles the shop added (these can be removed). */
    @GetMapping("/custom")
    public List<String> custom() {
        return profiles.customNames();
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please choose a profile file first."));
        }
        try {
            String name = profiles.save(file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.ok(Map.of("name", name));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not save the profile file."));
        }
    }

    // The name travels as a query parameter (?name=...) so the ".icc" ending is never cut off the URL
    @DeleteMapping
    public ResponseEntity<Map<String, String>> delete(@RequestParam("name") String name) {
        try {
            if (profiles.delete(name)) {
                return ResponseEntity.ok(Map.of("removed", name));
            }
            return ResponseEntity.status(404).body(Map.of("error", "That profile was not found, or it is built in and cannot be removed."));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Could not remove the profile file."));
        }
    }
}