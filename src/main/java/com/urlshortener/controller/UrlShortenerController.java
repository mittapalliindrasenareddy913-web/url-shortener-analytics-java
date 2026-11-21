package com.urlshortener.controller;

import com.urlshortener.model.UrlMapping;
import com.urlshortener.service.UrlShortenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UrlShortenerController {

    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/urls")
    public ResponseEntity<List<UrlMapping>> getAllUrls() {
        return ResponseEntity.ok(service.getAllUrls());
    }

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(@RequestBody Map<String, String> request) {
        String originalUrl = request.get("originalUrl");
        String customAlias = request.get("customAlias");
        String title = request.get("title");

        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "originalUrl is required"));
        }

        try {
            UrlMapping mapping = service.shortenUrl(originalUrl, customAlias, title);
            return ResponseEntity.ok(mapping);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/{code}")
    public ResponseEntity<?> getAnalytics(@PathVariable String code) {
        try {
            Map<String, Object> analytics = service.getAnalytics(code);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
