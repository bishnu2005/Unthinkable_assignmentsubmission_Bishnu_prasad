package com.analyzer.controller;

import com.analyzer.model.JobState;
import com.analyzer.service.ExtractionService;
import com.analyzer.service.GroqAiService;
import com.analyzer.service.GeminiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    @Autowired
    private ExtractionService extractionService;

    @Autowired
    private GroqAiService groqAiService;

    @Autowired
    private GeminiAiService geminiAiService;

    private final Map<String, JobState> jobRegistry = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "message", "Backend is running smoothly!");
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") java.util.List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No files uploaded"));
        }

        try {
            java.util.List<byte[]> fileBytesList = new java.util.ArrayList<>();
            java.util.List<String> contentTypes = new java.util.ArrayList<>();

            for (MultipartFile file : files) {
                fileBytesList.add(file.getBytes());
                contentTypes.add(file.getContentType());
            }

            String jobId = java.util.UUID.randomUUID().toString();
            JobState initialJob = new JobState();
            initialJob.status = "PENDING";
            jobRegistry.put(jobId, initialJob);

            CompletableFuture.runAsync(() -> {
                JobState job = jobRegistry.get(jobId);
                job.status = "PROCESSING";

                try {
                    // PDF PROCESSING (TEXT) - Groq remains primary here for raw speed
                    if (contentTypes.get(0).equalsIgnoreCase("application/pdf")) {
                        String extractedText = extractionService.extractText(fileBytesList.get(0), contentTypes.get(0));

                        if (extractedText == null || extractedText.trim().isEmpty()) {
                            throw new Exception("No readable text could be found in this PDF.");
                        }

                        // Normalize whitespace
                        extractedText = extractedText.replaceAll("\\s+", " ").trim();

                        // HARD CAP: Keeps it under Groq's 8000 TPM limit
                        if (extractedText.length() > 12000) {
                            extractedText = extractedText.substring(0, 12000);
                        }

                        job.extractedText = extractedText;
                        job.aiAnalysis = groqAiService.analyzeText(extractedText);

                    }
                    // IMAGE CAROUSEL PROCESSING (VISION) - Swapped to Gemini Primary
                    else {
                        try {
                            System.out.println("Attempting analysis with Gemini (Primary)...");
                            job.aiAnalysis = geminiAiService.analyzeImagesWithGemini(fileBytesList, contentTypes);
                            job.extractedText = "[Processed " + files.size() + " high-res images via Gemini Vision API]";
                        } catch (Exception geminiException) {
                            System.out.println("Gemini Rate Limit/Error Hit. Failing over to Groq (Secondary)...");
                            System.out.println("Gemini Error Details: " + geminiException.getMessage());

                            try {
                                job.aiAnalysis = groqAiService.analyzeImages(fileBytesList, contentTypes);
                                job.extractedText = "[Processed " + files.size() + " high-res images via Groq Fallback API]";
                            } catch (Exception groqException) {
                                throw new Exception("Both Gemini and Groq APIs failed. Groq Error: " + groqException.getMessage());
                            }
                        }
                    }

                    job.status = "COMPLETED";
                } catch (Throwable e) {
                    job.status = "FAILED";
                    job.error = e.getMessage() != null ? e.getMessage() : "An unexpected server error occurred.";
                }
            });

            return ResponseEntity.ok(Map.of("jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start job"));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobState> getJobStatus(@PathVariable String jobId) {
        JobState job = jobRegistry.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}