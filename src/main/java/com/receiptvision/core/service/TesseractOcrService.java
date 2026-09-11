package com.receiptvision.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OCR implementation based on the {@code tesseract} CLI installed in the OS /
 * Docker image. Using the CLI (instead of a JNI binding) keeps the deployment
 * simple: only {@code apt install tesseract-ocr tesseract-ocr-fas} is needed.
 */
@Service
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    private final String languages;
    private final String psm;
    private final long timeoutSeconds;
    private final String tesseractCommand;

    public TesseractOcrService(
            @Value("${app.ocr.languages:fas+eng}") String languages,
            @Value("${app.ocr.psm:3}") String psm,
            @Value("${app.ocr.timeout-seconds:30}") long timeoutSeconds,
            @Value("${app.ocr.command:tesseract}") String tesseractCommand) {
        this.languages = languages;
        this.psm = psm;
        this.timeoutSeconds = timeoutSeconds;
        this.tesseractCommand = tesseractCommand;
    }

    @Override
    public String extractText(InputStream imageStream, String contentType) {
        Path tempFile = null;
        try {
            String suffix = suffixFor(contentType);
            tempFile = Files.createTempFile("receipt-", suffix);
            try (InputStream in = imageStream) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> command = new ArrayList<>();
            command.add(tesseractCommand);
            command.add(tempFile.toAbsolutePath().toString());
            command.add("stdout");
            command.add("-l");
            command.add(languages);
            command.add("--psm");
            command.add(psm);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new OcrException("OCR timed out after " + timeoutSeconds + " seconds");
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.warn("Tesseract failed with exit code {}: {}", exitCode, stderr);
                throw new OcrException("OCR failed (exit " + exitCode + "): " + stderr.strip());
            }

            String text = stdout.strip();
            if (text.isEmpty()) {
                log.warn("Tesseract returned empty text. stderr: {}", stderr);
            }
            return text;
        } catch (IOException e) {
            throw new OcrException("Failed to run Tesseract binary: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("OCR was interrupted", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Could not delete temp OCR file {}", tempFile, e);
                }
            }
        }
    }

    @Override
    public String getLanguages() {
        return languages;
    }

    private static String suffixFor(String contentType) {
        if (contentType == null) {
            return ".img";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/tiff" -> ".tiff";
            case "image/bmp" -> ".bmp";
            default -> ".img";
        };
    }
}
