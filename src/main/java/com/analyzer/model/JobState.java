package com.analyzer.model;

public class JobState {
    public String status; // "PENDING", "PROCESSING", "COMPLETED", "FAILED"
    public String extractedText;
    public String aiAnalysis;
    public String error;

    public JobState() {
        this.status = "PENDING";
    }
}