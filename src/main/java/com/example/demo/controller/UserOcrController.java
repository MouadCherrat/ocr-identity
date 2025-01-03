package com.example.demo.controller;

import com.example.demo.model.OcrResult;
import com.example.demo.repository.OcrResultRepository;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ocr")
public class UserOcrController {

    @Autowired
    private OcrResultRepository ocrResultRepository;

    private final ITesseract tesseract;

    public UserOcrController() {
        this.tesseract = new Tesseract();
        // Configure le chemin des fichiers tessdata
        this.tesseract.setDatapath("/opt/homebrew/share/tessdata"); // Changez selon votre configuration
        this.tesseract.setLanguage("fra+ara+eng"); // Langues : français, arabe, anglais
        this.tesseract.setTessVariable("user_defined_dpi", "300");
    }

    // Endpoint pour extraire du texte d'une image
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractData(@RequestParam("document") MultipartFile document,
                                                           @RequestParam(value = "language", defaultValue = "fra+ara+eng") String language) {
        try {
            // Définit dynamiquement la langue
            tesseract.setLanguage(language);

            // Lire et prétraiter l'image
            BufferedImage originalImage = ImageIO.read(document.getInputStream());
            BufferedImage processedImage = processImage(originalImage);

            // Effectuer l'OCR
            String rawText = tesseract.doOCR(processedImage);

            // Nettoyer le texte brut
            String cleanedText = cleanExtractedText(rawText);

            // Extraire les champs spécifiques
            Map<String, String> fields = extractFields(cleanedText);

            // Enregistrer le résultat dans la base de données
            OcrResult result = new OcrResult();
            result.setFileName(document.getOriginalFilename());
            result.setExtractedText(cleanedText);
            result.setProcessedAt(LocalDateTime.now());
            result.setContentType(document.getContentType());
            result.setFileSize(document.getSize());
            ocrResultRepository.save(result);

            // Construire la réponse
            Map<String, Object> response = new HashMap<>();
            response.put("id", result.getId());
            response.put("text", cleanedText);
            response.put("fields", fields);
            response.put("originalFileName", document.getOriginalFilename());
            response.put("contentType", document.getContentType());
            response.put("size", document.getSize());
            response.put("processedAt", result.getProcessedAt());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de la lecture du fichier"));
        } catch (TesseractException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'extraction du texte de l'image"));
        }
    }

    // Endpoint pour récupérer un résultat OCR par ID
    @GetMapping("/{id}")
    public ResponseEntity<OcrResult> getOcrResult(@PathVariable Long id) {
        OcrResult ocrResult = ocrResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Résultat OCR introuvable pour l'ID : " + id));
        return ResponseEntity.ok(ocrResult);
    }

    // Endpoint pour récupérer tous les résultats OCR
    @GetMapping("/results")
    public List<OcrResult> getAllOcrResults() {
        return (List<OcrResult>) ocrResultRepository.findAll();
    }

    // Méthode pour prétraiter l'image
    private BufferedImage processImage(BufferedImage image) {
        // Convertir en niveaux de gris
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Égalisation de l'histogramme (ajuster les contrastes)
        grayImage = equalizeHistogram(grayImage);

        // Appliquer un filtre pour la netteté
        return sharpenImage(grayImage);
    }

    private BufferedImage equalizeHistogram(BufferedImage image) {
        // Utilisez OpenCV pour une égalisation d'histogramme optimale (si nécessaire)
        return image;
    }

    private BufferedImage sharpenImage(BufferedImage image) {
        float[] sharpenMatrix = {
                0f, -0.5f, 0f,
                -0.5f, 3f, -0.5f,
                0f, -0.5f, 0f
        };
        Kernel kernel = new Kernel(3, 3, sharpenMatrix);
        ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return convolveOp.filter(image, null);
    }

    // Méthode pour nettoyer le texte brut extrait
    private String cleanExtractedText(String text) {
        // Supprime les caractères non pertinents
        text = text.replaceAll("[^A-Za-z0-9À-ÿ\\s'/،.-]", "").trim();

        // Remplace les espaces multiples par un seul espace
        text = text.replaceAll("\\s{2,}", " ");

        return text;
    }

    // Méthode pour extraire des champs spécifiques
    private Map<String, String> extractFields(String text) {
        Map<String, String> fields = new HashMap<>();

        // Extraction du nom complet
        Pattern namePattern = Pattern.compile("([A-ZÀ-ÿ']+\\s+[A-ZÀ-ÿ']+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            fields.put("Nom complet", nameMatcher.group(1).trim());
        }

        // Extraction de la date de naissance
        Pattern dobPattern = Pattern.compile("(Née le|the|مزدادة بتاريخ)[:\\s]*(\\d{2}\\.\\d{2}\\.\\d{4}|\\d{2}/\\d{2}/\\d{4})");
        Matcher dobMatcher = dobPattern.matcher(text);
        if (dobMatcher.find()) {
            fields.put("Date de naissance", dobMatcher.group(2).trim());
        }

        // Extraction du lieu de naissance
        Pattern cityPattern = Pattern.compile("(à|ب)\\s+([A-ZÀ-ÿ']+)");
        Matcher cityMatcher = cityPattern.matcher(text);
        if (cityMatcher.find()) {
            fields.put("Lieu de naissance", cityMatcher.group(2).trim());
        }

        // Extraction du numéro de carte
        Pattern idNumberPattern = Pattern.compile("(\\d{4,10})");
        Matcher idNumberMatcher = idNumberPattern.matcher(text);
        if (idNumberMatcher.find()) {
            fields.put("Numéro de carte", idNumberMatcher.group(1).trim());
        }

        return fields;
    }
}
