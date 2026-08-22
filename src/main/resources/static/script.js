document.addEventListener("DOMContentLoaded", () => {
    const dropZone = document.getElementById("drop-zone");
    const fileInput = document.getElementById("file-input");
    const fileDetails = document.getElementById("file-details");
    const fileNameDisplay = document.getElementById("file-name");
    const fileSizeDisplay = document.getElementById("file-size");
    const removeBtn = document.getElementById("remove-btn");
    const uploadBtn = document.getElementById("upload-btn");

    const uploadContainer = document.getElementById("upload-container");
    const statusBox = document.getElementById("status-box");
    const cancelBtn = document.getElementById("cancel-btn");
    const resetBtn = document.getElementById("reset-btn");

    let selectedFiles = [];
    let loadingInterval = null;
    let activeJobId = null;

    dropZone.addEventListener("click", () => fileInput.click());

    dropZone.addEventListener("dragover", (e) => {
        e.preventDefault();
        dropZone.classList.add("dragover");
    });

    dropZone.addEventListener("dragleave", () => {
        dropZone.classList.remove("dragover");
    });

    dropZone.addEventListener("drop", (e) => {
        e.preventDefault();
        dropZone.classList.remove("dragover");
        if (e.dataTransfer.files.length > 0) handleFileSelection(Array.from(e.dataTransfer.files));
    });

    fileInput.addEventListener("change", (e) => {
        if (e.target.files.length > 0) handleFileSelection(Array.from(e.target.files));
    });

    function handleFileSelection(files) {
        if (files.length > 3) {
            showStaticStatus("AI Limit: You can only analyze a maximum of 3 images at a time.", true);
            selectedFiles = [];
            fileInput.value = "";
            return;
        }

        selectedFiles = files;

        if (selectedFiles.length === 1) {
            fileNameDisplay.textContent = selectedFiles[0].name;
            fileSizeDisplay.textContent = formatBytes(selectedFiles[0].size);
        } else {
            fileNameDisplay.textContent = `${selectedFiles.length} files selected (Carousel)`;
            const totalSize = selectedFiles.reduce((acc, file) => acc + file.size, 0);
            fileSizeDisplay.textContent = formatBytes(totalSize);
        }

        fileDetails.classList.remove("hidden");
        statusBox.classList.add("hidden");
    }

    removeBtn.addEventListener("click", resetToDefaultState);
    cancelBtn.addEventListener("click", resetToDefaultState);
    resetBtn.addEventListener("click", resetToDefaultState);

    function resetToDefaultState() {
        selectedFiles = [];
        fileInput.value = "";
        activeJobId = null;

        stopLoadingAnimation();

        statusBox.innerHTML = "";
        statusBox.className = "status-box hidden";
        statusBox.removeAttribute("style");

        fileDetails.classList.add("hidden");
        cancelBtn.classList.add("hidden");
        resetBtn.classList.add("hidden");

        uploadContainer.classList.remove("hidden");
        uploadBtn.disabled = false;
    }

    uploadBtn.addEventListener("click", async () => {
        if (selectedFiles.length === 0) return;

        uploadBtn.disabled = true;
        uploadContainer.classList.add("hidden");
        cancelBtn.classList.remove("hidden");

        startLoadingAnimation();

        const formData = new FormData();
        selectedFiles.forEach(file => formData.append("files", file));

        try {
            const response = await fetch("/api/upload", {
                method: "POST",
                body: formData
            });

            const result = await response.json();
            if (!response.ok) throw new Error(result.error || "File upload failed.");

            activeJobId = result.jobId;
            pollJobStatus(result.jobId);

        } catch (error) {
            cancelBtn.classList.add("hidden");
            resetBtn.classList.remove("hidden");
            showStaticStatus(`Error: ${error.message}`, true);
        }
    });

    async function pollJobStatus(jobId) {
        if (jobId !== activeJobId) return;

        try {
            const res = await fetch(`/api/status/${jobId}`);
            if (!res.ok) throw new Error("Failed to fetch status");

            const job = await res.json();
            if (jobId !== activeJobId) return;

            if (job.status === "PENDING" || job.status === "PROCESSING") {
                setTimeout(() => pollJobStatus(jobId), 2000);
            } else if (job.status === "COMPLETED") {
                stopLoadingAnimation();
                renderAnalysis(job.aiAnalysis);
                cancelBtn.classList.add("hidden");
                resetBtn.classList.remove("hidden");
            } else {
                stopLoadingAnimation();

                let finalError = job.error || "An unknown error occurred.";

                if (finalError.includes("rate_limit_exceeded") || finalError.includes("429")) {
                    finalError = "AI Rate Limit Reached! Please wait 60 seconds for the cooldown to clear before trying again.";
                }
                else if (finalError.includes("503") || finalError.includes("high demand") || finalError.includes("UNAVAILABLE")) {
                    finalError = "The AI servers are currently experiencing a surge in traffic. Please try again in a few moments.";
                }
                else if (finalError.includes("404") || finalError.includes("not found") || finalError.includes("NOT_FOUND")) {
                    finalError = "The AI model is temporarily offline for maintenance. Please try again later.";
                }
                else if (finalError.includes("No readable text")) {
                    finalError = "Could not detect any clear text in this PDF. Please ensure it is not a scanned image.";
                }
                else if (finalError.includes("{") && finalError.includes("}")) {
                    finalError = "The AI service is temporarily unavailable. Please try again later.";
                }

                showStaticStatus(`Analysis Failed: ${finalError}`, true);
                cancelBtn.classList.add("hidden");
                resetBtn.classList.remove("hidden");
            }
        } catch (error) {
            stopLoadingAnimation();
            showStaticStatus(`Polling Error: ${error.message}`, true);
            cancelBtn.classList.add("hidden");
            resetBtn.classList.remove("hidden");
        }
    }

    function startLoadingAnimation() {
        if (loadingInterval) return;

        statusBox.className = "status-box";
        statusBox.style = "background: transparent; border: none; padding: 0; margin-top: 24px; display: flex; gap: 16px;";
        statusBox.classList.remove("hidden");

        statusBox.innerHTML = `
            <div style="flex-shrink: 0; width: 32px; height: 32px; border-radius: 50%; background: #a8c7fa; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #131314;">AI</div>
            <div style="color: #a8c7fa; font-size: 1.05rem; display: flex; align-items: center; line-height: 1.6;" id="loading-text"></div>
        `;

        const loadingTextElement = document.getElementById("loading-text");

        const loadingMessages = [
            "Analyzing content... please wait",
            "Hang on a moment...",
            "AI is reviewing your images...",
            "Analysis is on the way...",
            "Running the engagement numbers..."
        ];

        let dots = 0;
        let messageIndex = 0;
        let tickCounter = 0;

        loadingInterval = setInterval(() => {
            dots = (dots + 1) % 4;

            if (tickCounter > 0 && tickCounter % 8 === 0) {
                messageIndex = (messageIndex + 1) % loadingMessages.length;
            }

            loadingTextElement.textContent = loadingMessages[messageIndex].replace(/\.+$/, "") + ".".repeat(dots);
            tickCounter++;
        }, 400);
    }

    function stopLoadingAnimation() {
        if (loadingInterval) {
            clearInterval(loadingInterval);
            loadingInterval = null;
        }
    }

    function renderAnalysis(jsonString) {
        try {
            const data = JSON.parse(jsonString);

            statusBox.className = "status-box";
            statusBox.style = "background: transparent; border: none; padding: 0; margin-top: 24px;";

            statusBox.innerHTML = `
                <div style="display: flex; gap: 16px;">
                    <!-- AI Avatar Icon -->
                    <div style="flex-shrink: 0; width: 32px; height: 32px; border-radius: 50%; background: #a8c7fa; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #131314;">AI</div>
                    
                    <!-- Chat Content (FIXED WORD WRAPPING HERE) -->
                    <div style="flex: 1; color: #e3e3e3; font-size: 1.05rem; line-height: 1.6; min-width: 0; word-break: normal; overflow-wrap: break-word;">
                        
                        <div style="display: flex; gap: 32px; margin-bottom: 24px; align-items: flex-start; padding-bottom: 20px; border-bottom: 1px solid #333537; flex-wrap: wrap;">
                            <div style="flex-shrink: 0; min-width: 140px;">
                                <div style="color: #c4c7c5; font-size: 0.9rem; margin-bottom: 4px; white-space: nowrap;">Engagement Score</div>
                                <div style="color: #a8c7fa; font-size: 2.2rem; font-weight: 600; line-height: 1;">${data.engagementScore}<span style="color: #5f6368; font-size: 1.1rem;">/100</span></div>
                            </div>
                            <div style="flex: 1; min-width: 200px;">
                                <div style="color: #c4c7c5; font-size: 0.9rem; margin-bottom: 4px;">Brand Tone</div>
                                <div style="color: #e3e3e3; font-size: 1.15rem; text-transform: capitalize; line-height: 1.4;">${data.tone}</div>
                            </div>
                        </div>
                        
                        <div style="margin-bottom: 12px; font-weight: 600; color: #a8c7fa; font-size: 1.1rem;">Key Strengths:</div>
                        <ul style="margin-left: 20px; margin-bottom: 28px; color: #e3e3e3;">
                            ${data.strengths.map(s => `<li style="margin-bottom: 10px;">${s}</li>`).join('')}
                        </ul>
                        
                        <div style="margin-bottom: 12px; font-weight: 600; color: #f2b8b5; font-size: 1.1rem;">Areas to Improve:</div>
                        <ul style="margin-left: 20px; margin-bottom: 8px; color: #e3e3e3;">
                            ${data.improvementSuggestions.map(s => `<li style="margin-bottom: 10px;">${s}</li>`).join('')}
                        </ul>
                    </div>
                </div>
            `;
        } catch (e) {
            showStaticStatus("Error: AI returned invalid format. Check console.", true);
        }
    }

    function showStaticStatus(message, isError) {
        stopLoadingAnimation();
        statusBox.textContent = message;
        statusBox.className = `status-box ${isError ? "error" : "success"}`;
        statusBox.style = isError ? "background-color: #3b1c1c; color: #f2b8b5; border: 1px solid #8c1d18; padding: 16px; border-radius: 8px; margin-top: 24px;" : "margin-top: 24px;";
        statusBox.classList.remove("hidden");
    }

    function formatBytes(bytes) {
        if (bytes === 0) return "0 Bytes";
        const k = 1024;
        const sizes = ["Bytes", "KB", "MB", "GB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
    }
});