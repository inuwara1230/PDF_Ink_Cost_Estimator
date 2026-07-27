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
    private static final String GOOGLE_CLIENT_ID = "886373325461-blnvf31hpc3gq4oipa7b69csd9k6i7fv.apps.googleusercontent.com";

    

    // --- ADDED: The new Google Login Endpoint ---
    @PostMapping("/google-login")
    public ResponseEntity<String> googleLogin(@RequestBody Map<String, String> body, HttpSession session) {
        String idTokenString = body.get("token");

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(GOOGLE_CLIENT_ID))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                
                // Retrieve user info from Google's verified payload
                String userEmail = payload.getEmail();

                // Store in session (integrates directly with PrinterSettingsController)
                session.setAttribute("loggedInUser", userEmail);

                return ResponseEntity.ok("Google Login Successful");
            } else {
                return ResponseEntity.status(401).body("Invalid Google ID token.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Authentication failed.");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate(); // Destroys the server session
        return ResponseEntity.ok("Logged out successfully");
    }
}