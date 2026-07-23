package com.inuwara.pdf_ink_cost_estimator;

//import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api")

public class AuthController {
    private final UserRepository userRepository;

    AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
public ResponseEntity<String> login(@RequestBody user loginRequest) {
    System.out.println("Attempting login for username: " + loginRequest.getUsername());
    Optional<user> userOptional = userRepository.findByUsername(loginRequest.getUsername());
    
    if (userOptional.isPresent()) {
        user dbUser = userOptional.get();
        System.out.println("User found. DB Password: " + dbUser.getPassword() + " | Request Password: " + loginRequest.getPassword());
        
        if (dbUser.getPassword() != null && dbUser.getPassword().equals(loginRequest.getPassword())) {
            return ResponseEntity.ok("Login successful");
        } else {
            System.out.println("Reason: Passwords do not match.");
        }
    } else {
        System.out.println("Reason: Username not found in database.");
    }
    
    return ResponseEntity.status(401).body("Invalid username or password");
}
}
