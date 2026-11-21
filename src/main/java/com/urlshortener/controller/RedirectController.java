package com.urlshortener.controller;

import com.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.util.Optional;

@Controller
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
    public ResponseEntity<Void> redirectToOriginal(@PathVariable String shortCode, HttpServletRequest request) {
        // Exclude static assets or API paths
        if (shortCode.equalsIgnoreCase("api") || shortCode.equalsIgnoreCase("index.html") || shortCode.equalsIgnoreCase("favicon.ico")) {
            return ResponseEntity.notFound().build();
        }

        String referrer = request.getHeader(HttpHeaders.REFERER);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String ip = request.getRemoteAddr();

        Optional<String> targetUrlOpt = service.resolveAndTrack(shortCode, referrer, userAgent, ip);

        if (targetUrlOpt.isPresent()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(targetUrlOpt.get()));
            return new ResponseEntity<>(headers, HttpStatus.FOUND); // HTTP 302 Found
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
