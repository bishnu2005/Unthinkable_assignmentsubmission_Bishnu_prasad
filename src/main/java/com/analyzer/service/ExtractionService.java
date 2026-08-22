package com.analyzer.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class ExtractionService {

    public String extractText(byte[] fileBytes, String contentType) throws Exception {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return extractTextFromPdf(fileBytes);
        } else {
            return extractTextFromImage(fileBytes);
        }
    }

    private String extractTextFromPdf(byte[] fileBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text.trim();
        }
    }

    private String extractTextFromImage(byte[] fileBytes) throws Exception {
        System.setProperty("jna.library.path", "/opt/homebrew/lib");

        ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
        BufferedImage image = ImageIO.read(bais);
        if (image == null) {
            throw new IOException("Could not read image file. It might be corrupted.");
        }

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/opt/homebrew/share/tessdata");
        tesseract.setLanguage("eng");

        String text = tesseract.doOCR(image);
        return text.trim();
    }
}