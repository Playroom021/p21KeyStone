package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        var user = principal.getUser();
        return ResponseEntity.ok(Map.of(
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        ));
    }

    @GetMapping("/dashboard/manager")
    public ResponseEntity<?> managerHome(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(Map.of("message", "Welcome, Manager " + principal.getUser().getFullName()));
    }

    @GetMapping("/dashboard/customer")
    public ResponseEntity<?> customerHome(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(Map.of("message", "Welcome, " + principal.getUser().getFullName()));
    }

    @GetMapping("/dashboard/worker")
    public ResponseEntity<?> workerHome(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(Map.of("message", "Welcome, " + principal.getUser().getFullName()));
    }

    @GetMapping("/dashboard/developer")
    public ResponseEntity<?> developerHome(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(Map.of("message", "Welcome, " + principal.getUser().getFullName()));
    }
}
