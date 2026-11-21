package com.urlshortener.service;

import com.urlshortener.model.ClickAnalytics;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.ClickAnalyticsRepository;
import com.urlshortener.repository.UrlMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

@Service
public class UrlShortenerService {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 7;
    private final SecureRandom random = new SecureRandom();

    private final UrlMappingRepository urlRepository;
    private final ClickAnalyticsRepository analyticsRepository;

    public UrlShortenerService(UrlMappingRepository urlRepository, ClickAnalyticsRepository analyticsRepository) {
        this.urlRepository = urlRepository;
        this.analyticsRepository = analyticsRepository;
    }

    public List<UrlMapping> getAllUrls() {
        return urlRepository.findAll();
    }

    @Transactional
    public UrlMapping shortenUrl(String originalUrl, String customAlias, String title) {
        // Validate URL format
        if (originalUrl == null || (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://"))) {
            originalUrl = "https://" + originalUrl;
        }

        String shortCode;
        if (customAlias != null && !customAlias.trim().isEmpty()) {
            String sanitizedAlias = customAlias.trim().toLowerCase().replaceAll("[^a-zA-Z0-9_-]", "");
            if (urlRepository.existsByCustomAlias(sanitizedAlias) || urlRepository.existsByShortCode(sanitizedAlias)) {
                throw new IllegalArgumentException("Custom alias '" + customAlias + "' is already in use!");
            }
            shortCode = sanitizedAlias;
            customAlias = sanitizedAlias;
        } else {
            shortCode = generateUniqueShortCode();
            customAlias = null;
        }

        UrlMapping mapping = new UrlMapping(originalUrl, shortCode, customAlias, title);
        return urlRepository.save(mapping);
    }

    @Transactional
    public Optional<String> resolveAndTrack(String codeOrAlias, String referrer, String userAgent, String ip) {
        Optional<UrlMapping> mappingOpt = urlRepository.findByShortCode(codeOrAlias)
                .or(() -> urlRepository.findByCustomAlias(codeOrAlias));

        if (mappingOpt.isEmpty() || !mappingOpt.get().isActive()) {
            return Optional.empty();
        }

        UrlMapping mapping = mappingOpt.get();
        mapping.incrementClicks();
        urlRepository.save(mapping);

        // Parse user agent
        String browser = detectBrowser(userAgent);
        String os = detectOS(userAgent);

        ClickAnalytics analytics = new ClickAnalytics(mapping, referrer, browser, os, ip, "India");
        analyticsRepository.save(analytics);

        return Optional.of(mapping.getOriginalUrl());
    }

    public Map<String, Object> getAnalytics(String codeOrAlias) {
        Optional<UrlMapping> mappingOpt = urlRepository.findByShortCode(codeOrAlias)
                .or(() -> urlRepository.findByCustomAlias(codeOrAlias));

        if (mappingOpt.isEmpty()) {
            throw new NoSuchElementException("URL not found");
        }

        UrlMapping url = mappingOpt.get();
        List<ClickAnalytics> clicks = analyticsRepository.findByUrlMappingIdOrderByClickedAtDesc(url.getId());

        Map<String, Integer> browserCounts = new HashMap<>();
        Map<String, Integer> osCounts = new HashMap<>();
        Map<String, Integer> referrerCounts = new HashMap<>();

        for (ClickAnalytics c : clicks) {
            browserCounts.merge(c.getBrowser(), 1, Integer::sum);
            osCounts.merge(c.getOperatingSystem(), 1, Integer::sum);
            referrerCounts.merge(c.getReferrer(), 1, Integer::sum);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("url", url);
        response.put("totalClicks", url.getClicksCount());
        response.put("browsers", browserCounts);
        response.put("operatingSystems", osCounts);
        response.put("referrers", referrerCounts);
        response.put("recentClicks", clicks.stream().limit(20).toList());

        return response;
    }

    private String generateUniqueShortCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(BASE62_CHARS.charAt(random.nextInt(BASE62_CHARS.length())));
            }
            code = sb.toString();
        } while (urlRepository.existsByShortCode(code));
        return code;
    }

    private String detectBrowser(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Safari")) return "Safari";
        if (ua.contains("Firefox")) return "Firefox";
        return "Other";
    }

    private String detectOS(String ua) {
        if (ua == null) return "Unknown";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac")) return "MacOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "Other";
    }
}
