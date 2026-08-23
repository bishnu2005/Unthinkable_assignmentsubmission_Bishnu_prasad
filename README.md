# Social Media Content Analyzer 

**Live Application URL:** [https://unthinkable-assignmentsubmission-bishnu.onrender.com](https://unthinkable-assignmentsubmission-bishnu.onrender.com)

## Technical Write-up & Approach
For this assessment, I built a cloud-native Social Media Content Analyzer using a Spring Boot (Java 21) backend and a vanilla HTML/CSS/JS frontend. 

To handle text extraction and content analysis, I bypassed legacy OCR tools and implemented a next-generation multimodal AI pipeline. The core architecture relies on an **Active Failover (Circuit Breaker)** pattern. Google Gemini (`gemini-2.5-flash`) handles complex high-resolution image processing, but if rate limits or API timeouts occur, the backend seamlessly and instantly routes the request to Groq (`gpt-oss-20b`) as a fallback, ensuring zero downtime and high availability.

The frontend features an asynchronous status-polling mechanism against a `ConcurrentHashMap` job registry in the backend, allowing the UI to update dynamically without freezing the browser thread. All sensitive API keys are strictly managed via environment variables and are not committed to source control. Finally, the application is containerized using a multi-stage Docker build (Eclipse Temurin Alpine) for optimized memory usage and deployed live on Render's cloud infrastructure.

## Tech Stack
* **Backend:** Java 21, Spring Boot
* **Frontend:** HTML5, CSS3 (Flexbox), Vanilla JavaScript
* **AI/ML Integration:** Google Gemini API, Groq API
* **Deployment:** Docker, Render
