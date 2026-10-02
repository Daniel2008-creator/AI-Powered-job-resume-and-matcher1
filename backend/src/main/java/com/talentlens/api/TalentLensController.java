package com.talentlens.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
public class TalentLensController {
    private final AuthService authService;
    private final MatcherService matcherService;

    public TalentLensController(AuthService authService, MatcherService matcherService) {
        this.authService = authService;
        this.matcherService = matcherService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "engine", "tfidf-skill-hybrid");
    }

    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@RequestBody @jakarta.validation.Valid AuthRequest request) {
        if (!request.email().contains("@")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Enter a valid email address");
        }
        authService.createUser(request.email(), request.password());
        return authService.createSession(request.email(), request.password());
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody @jakarta.validation.Valid AuthRequest request) {
        Map<String, Object> session = authService.createSession(request.email(), request.password());
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        return session;
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Map.of("user", requireUser(authorization));
    }

    @PostMapping("/api/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = tokenFrom(authorization);
        if (token != null) {
            authService.deleteSession(token);
        }
        return Map.of("status", "signed_out");
    }

    @PostMapping("/api/analyze")
    public Map<String, Object> analyze(
            @RequestBody @jakarta.validation.Valid AnalyzeRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireUser(authorization);
        return matcherService.analyze(request.job_description(), request.candidates());
    }

    @PostMapping(value = "/api/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> extract(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireUser(authorization);
        String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
        try {
            return Map.of("filename", filename, "text", readDocument(filename, file.getBytes()));
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }
    }

    @PostMapping("/api/export")
    public ResponseEntity<byte[]> export(
            @RequestBody @jakarta.validation.Valid AnalyzeRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireUser(authorization);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) matcherService
                .analyze(request.job_description(), request.candidates()).get("results");
        StringBuilder csv = new StringBuilder(
                "candidate,score,matched_skills,missing_skills,experience_signal\r\n");
        for (Map<String, Object> result : results) {
            List<String> fields = List.of(
                    csvField(result.get("candidate")),
                    csvField(result.get("score")),
                    csvField(String.join(", ", (List<String>) result.get("matched_skills"))),
                    csvField(String.join(", ", (List<String>) result.get("missing_skills"))),
                    csvField(result.get("experience_signal")));
            csv.append(String.join(",", fields)).append("\r\n");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("talentlens-results.csv").build());
        return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> requireUser(String authorization) {
        String token = tokenFrom(authorization);
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        }
        Map<String, Object> user = authService.userForToken(token);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired");
        }
        return user;
    }

    private String tokenFrom(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).strip();
        return token.isEmpty() ? null : token;
    }

    private String readDocument(String filename, byte[] content) {
        String suffix = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        try {
            if (suffix.equals("pdf")) {
                try (PDDocument document = Loader.loadPDF(content)) {
                    return new PDFTextStripper().getText(document);
                }
            }
            if (suffix.equals("docx")) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
                    return String.join("\n", document.getParagraphs().stream().map(p -> p.getText()).toList());
                }
            }
            if (List.of("txt", "md").contains(suffix)) {
                return new String(content, StandardCharsets.UTF_8);
            }
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use PDF, DOCX, TXT, or MD files");
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded document");
        }
    }

    private String csvField(Object value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}