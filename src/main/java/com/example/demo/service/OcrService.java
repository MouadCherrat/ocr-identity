package com.example.demo.service;

import com.example.demo.model.OcrResult;
import com.example.demo.repository.OcrResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrService {

    @Value("${ocr.api.key}")
    private String apiKey;

    private final OcrResultRepository ocrResultRepository;
    private final RestTemplate restTemplate;

    public OcrService(OcrResultRepository ocrResultRepository, RestTemplate restTemplate) {
        this.ocrResultRepository = ocrResultRepository;
        this.restTemplate = restTemplate;
    }

    public OcrResult processImage(MultipartFile file) throws IOException {
        String apiUrl = "https://api.ocr.space/parse/image";

        // Préparer les en-têtes
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Préparer le corps de la requête
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("apikey", apiKey);
        body.add("language", "fre");
        body.add("isOverlayRequired", "false");
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Appeler l'API OCR.space
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && !(boolean) responseBody.get("IsErroredOnProcessing")) {
                String extractedText = (String) ((Map) ((Map[]) responseBody.get("ParsedResults"))[0]).get("ParsedText");

                // Enregistrer les résultats dans la base de données
                OcrResult ocrResult = new OcrResult();
                ocrResult.setFileName(file.getOriginalFilename());
                ocrResult.setExtractedText(extractedText);
                ocrResult.setProcessedAt(LocalDateTime.now());
                ocrResult.setContentType(file.getContentType());
                ocrResult.setFileSize(file.getSize());

                return ocrResultRepository.save(ocrResult);
            } else {
                throw new RuntimeException("Erreur dans le traitement OCR : " + responseBody.get("ErrorMessage"));
            }
        } else {
            throw new RuntimeException("Erreur HTTP : " + response.getStatusCode());
        }
    }
}
