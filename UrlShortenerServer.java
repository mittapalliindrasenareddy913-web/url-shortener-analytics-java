import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enterprise URL Shortener & Analytics Server
 * Runs on http://localhost:8081
 */
public class UrlShortenerServer {

    private static final int PORT = 8081;
    private static final Path PUBLIC_DIR = Paths.get("public").toAbsolutePath();
    private static final String BASE_URL = "http://localhost:" + PORT + "/";
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static class UrlItem {
        public long id;
        public String originalUrl;
        public String shortCode;
        public String customAlias;
        public String title;
        public int clicksCount;
        public String createdAt;

        public UrlItem(long id, String originalUrl, String shortCode, String customAlias, String title, int clicks, String createdAt) {
            this.id = id;
            this.originalUrl = originalUrl;
            this.shortCode = shortCode;
            this.customAlias = customAlias;
            this.title = title;
            this.clicksCount = clicks;
            this.createdAt = createdAt;
        }
    }

    public static class ClickEvent {
        public long id;
        public long urlId;
        public String shortCode;
        public String clickedAt;
        public String referrer;
        public String browser;
        public String os;
        public String ip;
        public String country;

        public ClickEvent(long id, long urlId, String shortCode, String clickedAt, String referrer, String browser, String os, String ip, String country) {
            this.id = id;
            this.urlId = urlId;
            this.shortCode = shortCode;
            this.clickedAt = clickedAt;
            this.referrer = referrer;
            this.browser = browser;
            this.os = os;
            this.ip = ip;
            this.country = country;
        }
    }

    private static final List<UrlItem> urls = new CopyOnWriteArrayList<>();
    private static final List<ClickEvent> clicks = new CopyOnWriteArrayList<>();
    private static final AtomicLong urlIdCounter = new AtomicLong(1);
    private static final AtomicLong clickIdCounter = new AtomicLong(1);

    public static void main(String[] args) throws Exception {
        initSampleData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API Endpoints
        server.createContext("/api/urls", new UrlsHandler());
        server.createContext("/api/shorten", new ShortenHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());

        // Redirect & Static File Routing
        server.createContext("/", new RootHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("=========================================================");
        System.out.println("  URL Shortener & Analytics Service is RUNNING!");
        System.out.println("  Dashboard URL : http://localhost:" + PORT);
        System.out.println("=========================================================");
    }

    private static void initSampleData() {
        if (urls.isEmpty()) {
            addSample("https://github.com/mittapalliindrasenareddy913-web", "gh913", "my-github", "GitHub Portfolio Profile", 48);
            addSample("https://spring.io/projects/spring-boot", "spboot", "spring-docs", "Spring Boot Official Documentation", 31);
            addSample("https://www.google.com", "googl", null, "Google Search Engine", 19);
        }
    }

    private static void addSample(String longUrl, String code, String alias, String title, int clicksCount) {
        long id = urlIdCounter.getAndIncrement();
        String dt = LocalDateTime.now().minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        UrlItem item = new UrlItem(id, longUrl, code, alias, title, clicksCount, dt);
        urls.add(item);

        // Generate sample click analytics
        String[] browsers = {"Chrome", "Safari", "Edge", "Firefox"};
        String[] osList = {"Windows", "MacOS", "Android", "iOS", "Linux"};
        String[] referrers = {"Direct / Bookmark", "linkedin.com", "twitter.com", "google.com", "github.com"};

        for (int i = 0; i < Math.min(clicksCount, 15); i++) {
            String cdt = LocalDateTime.now().minusHours(i * 4 + 1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            clicks.add(new ClickEvent(
                    clickIdCounter.getAndIncrement(),
                    id,
                    code,
                    cdt,
                    referrers[i % referrers.length],
                    browsers[i % browsers.length],
                    osList[i % osList.length],
                    "127.0.0.1",
                    "India"
            ));
        }
    }

    // --- Handlers ---
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();

            // 1. Root or static files
            if (path.equals("/") || path.equals("/index.html") || path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".svg") || path.endsWith(".ico")) {
                serveStatic(ex, path.equals("/") ? "/index.html" : path);
                return;
            }

            // 2. Short URL redirection (e.g. /gh913 or /r/gh913)
            String code = path.startsWith("/r/") ? path.substring(3) : path.substring(1);
            UrlItem target = findByCodeOrAlias(code);

            if (target != null) {
                // Track click event
                target.clicksCount++;
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String ua = ex.getRequestHeaders().getFirst("User-Agent");
                String ref = ex.getRequestHeaders().getFirst("Referer");

                clicks.add(new ClickEvent(
                        clickIdCounter.getAndIncrement(),
                        target.id,
                        target.shortCode,
                        now,
                        ref != null ? ref : "Direct / Bookmark",
                        parseBrowser(ua),
                        parseOS(ua),
                        ex.getRemoteAddress().getAddress().getHostAddress(),
                        "India"
                ));

                // 302 Found Redirect
                ex.getResponseHeaders().set("Location", target.originalUrl);
                ex.sendResponseHeaders(302, -1);
                ex.close();
            } else {
                sendResponse(ex, 404, "text/html", "<h1>404 - Short Link Not Found</h1><p><a href='/'>Go to Dashboard</a></p>");
            }
        }
    }

    static class UrlsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < urls.size(); i++) {
                    UrlItem u = urls.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"id\":").append(u.id).append(",")
                      .append("\"originalUrl\":\"").append(escape(u.originalUrl)).append("\",")
                      .append("\"shortCode\":\"").append(escape(u.shortCode)).append("\",")
                      .append("\"customAlias\":").append(u.customAlias != null ? "\"" + escape(u.customAlias) + "\"" : "null").append(",")
                      .append("\"shortUrl\":\"").append(BASE_URL).append(escape(u.customAlias != null ? u.customAlias : u.shortCode)).append("\",")
                      .append("\"title\":\"").append(escape(u.title)).append("\",")
                      .append("\"clicksCount\":").append(u.clicksCount).append(",")
                      .append("\"createdAt\":\"").append(escape(u.createdAt)).append("\"")
                      .append("}");
                }
                sb.append("]");
                sendResponse(ex, 200, "application/json", sb.toString());
            } else {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
            }
        }
    }

    static class ShortenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJson(readRequestBody(ex));
            String originalUrl = body.get("originalUrl");
            String customAlias = body.get("customAlias");
            String title = body.get("title");

            if (originalUrl == null || originalUrl.trim().isEmpty()) {
                sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"URL is required!\"}");
                return;
            }

            originalUrl = originalUrl.trim();
            if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
                originalUrl = "https://" + originalUrl;
            }

            String code;
            if (customAlias != null && !customAlias.trim().isEmpty()) {
                customAlias = customAlias.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
                if (findByCodeOrAlias(customAlias) != null) {
                    sendResponse(ex, 400, "application/json", "{\"success\":false,\"message\":\"Custom alias already in use!\"}");
                    return;
                }
                code = customAlias;
            } else {
                code = generateCode();
                customAlias = null;
            }

            if (title == null || title.trim().isEmpty()) {
                title = extractDomain(originalUrl);
            }

            long id = urlIdCounter.getAndIncrement();
            String dt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            UrlItem newItem = new UrlItem(id, originalUrl, code, customAlias, title.trim(), 0, dt);
            urls.add(0, newItem); // add to top

            String shortUrl = BASE_URL + (customAlias != null ? customAlias : code);
            String json = String.format("{\"success\":true,\"id\":%d,\"shortCode\":\"%s\",\"shortUrl\":\"%s\",\"originalUrl\":\"%s\",\"title\":\"%s\"}",
                    id, code, shortUrl, escape(originalUrl), escape(title));
            sendResponse(ex, 200, "application/json", json);
        }
    }

    static class AnalyticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String query = ex.getRequestURI().getQuery();
            String code = null;
            if (query != null && query.contains("code=")) {
                for (String p : query.split("&")) {
                    if (p.startsWith("code=")) code = URLDecoder.decode(p.substring(5), StandardCharsets.UTF_8);
                }
            }

            if (code == null) {
                // Global Analytics summary
                int totalClicks = 0;
                for (UrlItem u : urls) totalClicks += u.clicksCount;

                String json = String.format("{\"totalLinks\":%d,\"totalClicks\":%d,\"recentClicks\":%d}",
                        urls.size(), totalClicks, clicks.size());
                sendResponse(ex, 200, "application/json", json);
                return;
            }

            UrlItem target = findByCodeOrAlias(code);
            if (target == null) {
                sendResponse(ex, 404, "application/json", "{\"error\":\"URL not found\"}");
                return;
            }

            // Aggregate metrics
            Map<String, Integer> browsers = new LinkedHashMap<>();
            Map<String, Integer> osMap = new LinkedHashMap<>();
            Map<String, Integer> referrers = new LinkedHashMap<>();
            List<ClickEvent> linkClicks = new ArrayList<>();

            for (ClickEvent c : clicks) {
                if (c.urlId == target.id) {
                    browsers.merge(c.browser, 1, Integer::sum);
                    osMap.merge(c.os, 1, Integer::sum);
                    referrers.merge(c.referrer, 1, Integer::sum);
                    linkClicks.add(c);
                }
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"shortCode\":\"").append(escape(target.shortCode)).append("\",");
            sb.append("\"originalUrl\":\"").append(escape(target.originalUrl)).append("\",");
            sb.append("\"title\":\"").append(escape(target.title)).append("\",");
            sb.append("\"totalClicks\":").append(target.clicksCount).append(",");
            sb.append("\"createdAt\":\"").append(target.createdAt).append("\",");

            // Browsers
            sb.append("\"browsers\":{");
            int i = 0;
            for (Map.Entry<String, Integer> e : browsers.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            }
            sb.append("},");

            // OS
            sb.append("\"os\":{");
            i = 0;
            for (Map.Entry<String, Integer> e : osMap.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            }
            sb.append("},");

            // Referrers
            sb.append("\"referrers\":{");
            i = 0;
            for (Map.Entry<String, Integer> e : referrers.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            }
            sb.append("},");

            // Recent visits
            sb.append("\"recentVisits\":[");
            int max = Math.min(linkClicks.size(), 10);
            for (int j = 0; j < max; j++) {
                ClickEvent ce = linkClicks.get(linkClicks.size() - 1 - j);
                if (j > 0) sb.append(",");
                sb.append("{\"clickedAt\":\"").append(ce.clickedAt)
                  .append("\",\"browser\":\"").append(ce.browser)
                  .append("\",\"os\":\"").append(ce.os)
                  .append("\",\"referrer\":\"").append(escape(ce.referrer))
                  .append("\",\"country\":\"").append(ce.country).append("\"}");
            }
            sb.append("]}");

            sendResponse(ex, 200, "application/json", sb.toString());
        }
    }

    // --- Helpers ---
    private static UrlItem findByCodeOrAlias(String code) {
        if (code == null) return null;
        for (UrlItem u : urls) {
            if (code.equalsIgnoreCase(u.shortCode) || (u.customAlias != null && code.equalsIgnoreCase(u.customAlias))) {
                return u;
            }
        }
        return null;
    }

    private static String generateCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
            }
            code = sb.toString();
        } while (findByCodeOrAlias(code) != null);
        return code;
    }

    private static String extractDomain(String url) {
        try {
            String clean = url.replace("https://", "").replace("http://", "");
            int slash = clean.indexOf('/');
            return slash > 0 ? clean.substring(0, slash) : clean;
        } catch (Exception e) {
            return "Web Link";
        }
    }

    private static String parseBrowser(String ua) {
        if (ua == null) return "Direct";
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Safari")) return "Safari";
        if (ua.contains("Firefox")) return "Firefox";
        return "Chrome";
    }

    private static String parseOS(String ua) {
        if (ua == null) return "Windows";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac")) return "MacOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "Windows";
    }

    private static void serveStatic(HttpExchange ex, String path) throws IOException {
        Path filePath = PUBLIC_DIR.resolve(path.substring(1)).normalize();
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendResponse(ex, 404, "text/plain", "404 Not Found");
            return;
        }

        String mime = "text/html";
        if (path.endsWith(".css")) mime = "text/css";
        else if (path.endsWith(".js")) mime = "application/javascript";
        else if (path.endsWith(".svg")) mime = "image/svg+xml";

        byte[] data = Files.readAllBytes(filePath);
        ex.getResponseHeaders().set("Content-Type", mime + "; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendResponse(HttpExchange ex, int status, String mime, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime + "; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
        }
        return map;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "");
    }
}
