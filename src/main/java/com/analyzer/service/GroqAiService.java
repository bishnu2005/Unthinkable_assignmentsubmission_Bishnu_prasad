package com.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

@Service
public class GroqAiService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String TEXT_PROMPT = """
        ROLE: Expert Social Media Auditor.
        TASK: Analyze social media content text for hook potential, clarity, and conversion.
        MANDATORY RULES:
        - Quote specific lines, metrics, or words from the text in your strengths and improvements.
        - Output ONLY raw valid JSON starting with { and ending with }.
        
        SCHEMA:
        {
          "engagementScore": <integer 1-100>,
          "tone": "<1-3 words>",
          "strengths": ["<detailed point citing specific text>", "<detailed point citing specific text>"],
          "improvementSuggestions": ["<actionable advice with specific example>", "<actionable advice with specific example>"]
        }
        """;

    private static final String VISION_PROMPT = """
        ROLE: Expert Social Media Auditor.
        TASK: Audit ALL provided social media images. They may be a carousel of posts, memes, or profiles.
        MANDATORY RULES:
        - Analyze EVERY image provided. Do not ignore any image. Evaluate how they work together.
        - Analyze specific elements: text, branding, humor, visual hierarchy, and layout clarity.
        - Quote exact text or numbers visible in the images.
        - Output ONLY raw valid JSON starting with { and ending with }.

        SCHEMA:
        {
          "engagementScore": <integer 1-100>,
          "tone": "<1-3 words>",
          "strengths": ["<point citing visible elements from ANY of the images>", "<point citing visible elements>"],
          "improvementSuggestions": ["<actionable advice for the images>", "<actionable advice>"]
        }
        """;

    private byte[] compressImage(byte[] imageBytes, String mimeType) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage original = ImageIO.read(bais);
            if (original == null) return imageBytes;

            int maxDim = 800;

            int width = original.getWidth();
            int height = original.getHeight();

            if (width <= maxDim && height <= maxDim) return imageBytes;

            double scale = Math.min((double) maxDim / width, (double) maxDim / height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);

            int type = mimeType.contains("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage resized = new BufferedImage(newWidth, newHeight, type);

            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String format = mimeType.replace("image/", "");
            if (format.equals("jpeg")) format = "jpg";

            ImageIO.write(resized, format, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("Image compression failed, falling back to original size.");
            return imageBytes;
        }
    }

    public String analyzeText(String content) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", "openai/gpt-oss-20b",
                "temperature", 0.1,
                "max_tokens", 1500, // REVERTED: Restored full output runway
                "response_format", Map.of("type", "json_object"),
                "messages", new Object[]{
                        Map.of("role", "system", "content", TEXT_PROMPT),
                        Map.of("role", "user", "content", content)
                }
        );
        return executeGroqCall(requestBody);
    }

    public String analyzeImages(java.util.List<byte[]> imageBytesList, java.util.List<String> mimeTypes) throws Exception {
        java.util.List<Map<String, Object>> contentArray = new java.util.ArrayList<>();
        contentArray.add(Map.of("type", "text", "text", VISION_PROMPT));

        for (int i = 0; i < imageBytesList.size(); i++) {
            byte[] optimizedBytes = compressImage(imageBytesList.get(i), mimeTypes.get(i));

            String base64Image = Base64.getEncoder().encodeToString(optimizedBytes);

            String dataUrl = "data:" + mimeTypes.get(i) + ";base64," + base64Image;
            contentArray.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }

        Map<String, Object> requestBody = Map.of(
                "model", "qwen/qwen3.6-27b",
                "temperature", 0.1,
                "max_tokens", 1500, // REVERTED: Restored full output runway
                "response_format", Map.of("type", "json_object"),
                "messages", new Object[]{
                        Map.of("role", "user", "content", contentArray)
                }
        );
        return executeGroqCall(requestBody);
    }

    public String summarizeChunk(String chunk) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", "openai/gpt-oss-20b",
                "temperature", 0.1,
                "messages", new Object[]{
                        Map.of("role", "system", "content", "Extract key data points, text quotes, and metrics from this document section in concise bullets:"),
                        Map.of("role", "user", "content", chunk)
                }
        );
        return executeGroqCall(requestBody);
    }

    private String executeGroqCall(Map<String, Object> requestBody) throws Exception {
        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Authorization", "Bearer " + groqApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}