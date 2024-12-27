package com.example.demo.controller;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.OcrResult;
import com.example.demo.repository.OcrResultRepository;

@RestController
@RequestMapping("/api/v1/ocr")
public class UserOcrController {

    @Autowired
    private OcrResultRepository ocrResultRepository;

    private final ITesseract tesseract;

    public UserOcrController() {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("/opt/homebrew/share/tessdata"); // Chemin vers tessdata
        this.tesseract.setLanguage("fra+ara+eng"); // Langues : français, arabe et anglais
        this.tesseract.setTessVariable("user_defined_dpi", "300");
        this.tesseract.setTessVariable("preserve_interword_spaces", "1"); // Maintenir les espaces entre mots
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAndExtractText(@RequestParam("image") MultipartFile imageFile) {
        try {
            // Lire et prétraiter l'image
            BufferedImage image = ImageIO.read(imageFile.getInputStream());
            BufferedImage processedImage = processImage(image);

            // Extraire le texte brut
            String extractedText = tesseract.doOCR(processedImage);

            // Nettoyer et extraire les champs spécifiques
            String cleanedText = cleanText(extractedText);
            Map<String, String> fields = extractFields(cleanedText);

            // Sauvegarder les résultats dans la base de données
            OcrResult result = new OcrResult();
            result.setFileName(imageFile.getOriginalFilename());
            result.setExtractedText(cleanedText);
            result.setProcessedAt(LocalDateTime.now());
            result.setContentType(imageFile.getContentType());
            result.setFileSize(imageFile.getSize());
            ocrResultRepository.save(result);

            // Préparer la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("id", result.getId());
            response.put("fields", fields);
            response.put("rawText", cleanedText);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la lecture du fichier"));
        } catch (TesseractException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'extraction du texte de l'image"));
        }
    }

    private BufferedImage processImage(BufferedImage image) {
        // Redimensionner pour augmenter la résolution
        int scaledWidth = image.getWidth() * 2;
        int scaledHeight = image.getHeight() * 2;
        BufferedImage resizedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null); // Corrigé
        g.dispose();

        // Convertir en niveaux de gris
        BufferedImage grayImage = new BufferedImage(resizedImage.getWidth(), resizedImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        g = grayImage.createGraphics();
        g.drawImage(resizedImage, 0, 0, null);
        g.dispose();

        // Appliquer un seuil adaptatif pour binarisation
        for (int x = 0; x < grayImage.getWidth(); x++) {
            for (int y = 0; y < grayImage.getHeight(); y++) {
                int pixel = grayImage.getRGB(x, y) & 0xFF;
                grayImage.setRGB(x, y, (pixel < 150) ? 0x000000 : 0xFFFFFF);
            }
        }

        return grayImage;
    }

    private String cleanText(String text) {
        text = text.replaceAll("[^A-Za-z0-9À-ÿ\\s'/،.-]", "").trim();
        text = text.replaceAll("\\s{2,}", " ");
        text = text.replaceAll("(?m)^[ \t]*\r?\n", "");
        return text;
    }

    private Map<String, String> extractFields(String text) {
        Map<String, String> fields = new HashMap<>();

        // Nom complet
        Pattern namePattern = Pattern.compile("([A-ZÀ-ÿ']+\\s+[A-ZÀ-ÿ']+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            fields.put("Nom complet", nameMatcher.group(1).trim());
        }

        // Date de naissance
        Pattern dobPattern = Pattern.compile("(Née le|the|مزدادة بتاريخ)[:\\s]*(\\d{2}\\.\\d{2}\\.\\d{4}|\\d{2}/\\d{2}/\\d{4})");
        Matcher dobMatcher = dobPattern.matcher(text);
        if (dobMatcher.find()) {
            fields.put("Date de naissance", dobMatcher.group(2).trim());
        }

        // Lieu de naissance
        Pattern cityPattern = Pattern.compile("(à|ب)\\s+([A-ZÀ-ÿ']+)");
        Matcher cityMatcher = cityPattern.matcher(text);
        if (cityMatcher.find()) {
            fields.put("Lieu de naissance", cityMatcher.group(2).trim());
        }

        // Numéro de carte
        Pattern idNumberPattern = Pattern.compile("(\\d{4,10})");
        Matcher idNumberMatcher = idNumberPattern.matcher(text);
        if (idNumberMatcher.find()) {
            fields.put("Numéro de carte", idNumberMatcher.group(1).trim());
        }

        // Valable jusqu'à
        Pattern expiryPattern = Pattern.compile("(Valable jusqu['’]au|الى)[:\\s]*(\\d{2}\\.\\d{2}\\.\\d{4}|\\d{2}/\\d{2}/\\d{4})");
        Matcher expiryMatcher = expiryPattern.matcher(text);
        if (expiryMatcher.find()) {
            fields.put("Valable jusqu'à", expiryMatcher.group(2).trim());
        }

        return fields;
    }
}
