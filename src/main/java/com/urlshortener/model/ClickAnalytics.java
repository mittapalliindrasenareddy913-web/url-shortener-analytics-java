package com.urlshortener.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_analytics")
public class ClickAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private UrlMapping urlMapping;

    @Column(nullable = false)
    private LocalDateTime clickedAt = LocalDateTime.now();

    @Column(length = 255)
    private String referrer;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String operatingSystem;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 50)
    private String country;

    public ClickAnalytics() {}

    public ClickAnalytics(UrlMapping urlMapping, String referrer, String browser, String operatingSystem, String ipAddress, String country) {
        this.urlMapping = urlMapping;
        this.clickedAt = LocalDateTime.now();
        this.referrer = referrer != null && !referrer.isEmpty() ? referrer : "Direct / Unknown";
        this.browser = browser != null ? browser : "Chrome";
        this.operatingSystem = operatingSystem != null ? operatingSystem : "Windows";
        this.ipAddress = ipAddress != null ? ipAddress : "127.0.0.1";
        this.country = country != null ? country : "India";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UrlMapping getUrlMapping() { return urlMapping; }
    public void setUrlMapping(UrlMapping urlMapping) { this.urlMapping = urlMapping; }

    public LocalDateTime getClickedAt() { return clickedAt; }
    public void setClickedAt(LocalDateTime clickedAt) { this.clickedAt = clickedAt; }

    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
