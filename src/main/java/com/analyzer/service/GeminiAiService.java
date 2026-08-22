package com.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String SYSTEM_PROMPT = """
        ROLE: Expert Social Media Auditor.
        TASK: Audit ALL provided social media images. Evaluate them as a carousel.
        MANDATORY RULES:
        - Analyze text, branding, humor, visual hierarchy, and layout clarity.
        - Quote exact text or numbers visible in the images.
        - Output ONLY raw valid JSON.

        SCHEMA:
        {
          "engagementScore": <integer 1-100>,
          "tone": "<1-3 words>",
          "strengths": ["<point citing visible elements from ANY of the images>"],
          "improvementSuggestions": ["<actionable advice for the images>"]
        }
        """;

    public String analyzeImagesWithGemini(List<byte[]> imageBytesList, List<String> mimeTypes) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", SYSTEM_PROMPT));

        for (int i = 0; i < imageBytesList.size(); i++) {
            String base64Image = Base64.getEncoder().encodeToString(imageBytesList.get(i));
            parts.add(Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeTypes.get(i),
                            "data", base64Image
                    )
            ));
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
    }
}