package com.inuwara.pdf_ink_cost_estimator;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    
    // --- ADDED: Your Google Client ID ---
   
    



    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate(); // Destroys the server session
        return ResponseEntity.ok("Logged out successfully");
    }
}