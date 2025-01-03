import requests
import re
import cv2

def preprocess_image(image_path):
    """
    Prétraite l'image pour améliorer les résultats OCR.
    - Convertit en niveaux de gris.
    - Redimensionne pour une meilleure qualité OCR.
    - Applique un seuil adaptatif.
    """
    # Charger l'image
    image = cv2.imread(image_path)
    # Redimensionner pour augmenter la précision
    image = cv2.resize(image, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    # Convertir en niveaux de gris
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    # Appliquer un seuil adaptatif
    thresh = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
    )
    # Enregistrer l'image prétraitée (utile pour le débogage)
    cv2.imwrite("preprocessed_image.jpg", thresh)
    return "preprocessed_image.jpg"

def extract_text_with_ocr_space(image_path, api_key="K86289643888957", language="ara+fre"):
    """
    Utilise OCR.space API pour extraire le texte d'une image.
    """
    api_url = "https://api.ocr.space/parse/image"

    # Charger l'image (prétraitée)
    with open(image_path, "rb") as image_file:
        # Préparer les fichiers pour la requête
        files = {"file": image_file}
        
        # Préparer les paramètres pour l'API
        payload = {
            "apikey": api_key,          # Votre clé API OCR.space
            "language": language,       # Langue pour l'OCR : "ara+fra" pour l'arabe et le français
            "isOverlayRequired": False, # Désactive les overlays pour simplifier le résultat
        }

        # Envoyer la requête POST
        response = requests.post(api_url, data=payload, files=files)

    # Vérifier le statut de la réponse
    if response.status_code == 200:
        result = response.json()
        if result["IsErroredOnProcessing"]:
            # En cas d'erreur côté OCR
            print("Erreur OCR :", result["ErrorMessage"][0])
            return None

        # Retourner le texte extrait
        return result["ParsedResults"][0]["ParsedText"]
    else:
        print("Erreur HTTP :", response.status_code)
        return None

def clean_extracted_text(text):
    """
    Nettoie le texte brut extrait pour le rendre plus lisible.
    """
    text = re.sub(r"[^\w\sÀ-ÿء-ي]", " ", text)  # Garde les caractères valides
    text = re.sub(r"\s{2,}", " ", text)         # Réduit les espaces multiples
    return text.strip()

def extract_fields_from_text(text):
    """
    Analyse le texte extrait pour identifier des champs spécifiques.
    """
    fields = {}

    # Nom complet (en français)
    name_fr = re.search(r"([A-Z]+ [A-Z]+)", text)
    if name_fr:
        fields["Nom (FR)"] = name_fr.group(1)

    # Nom complet (en arabe)
    name_ar = re.search(r"[ء-ي]+ [ء-ي]+", text)
    if name_ar:
        fields["Nom (AR)"] = name_ar.group(0)

    # Date de naissance
    dob = re.search(r"(\d{2}\.\d{2}\.\d{4})", text)
    if dob:
        fields["Date de Naissance"] = dob.group(1)

    # Lieu de naissance
    birthplace = re.search(r"à\s+([A-Za-zÀ-ÿ ]+)", text)
    if birthplace:
        fields["Lieu de Naissance"] = birthplace.group(1)

    # CIN (Code d'identité nationale)
    cin = re.search(r"[A-Z]{2}\d{6}", text)
    if cin:
        fields["CIN"] = cin.group(0)

    return fields

if __name__ == "__main__":
    # Étape 1 : Image source
    image_path = "imaecin.jpeg"  # Chemin de votre image
    api_key = "votre_cle_api"   # Remplacez par votre clé API OCR.space

    # Étape 2 : Prétraitement de l'image
    preprocessed_image_path = preprocess_image(image_path)

    # Étape 3 : Extraction de texte avec OCR.space
    extracted_text = extract_text_with_ocr_space(preprocessed_image_path, api_key)

    if extracted_text:
        # Étape 4 : Nettoyage du texte brut
        cleaned_text = clean_extracted_text(extracted_text)
        print("\nTexte extrait nettoyé :")
        print(cleaned_text)

        # Étape 5 : Extraction des champs spécifiques
        extracted_fields = extract_fields_from_text(cleaned_text)
        print("\nChamps extraits :")
        for key, value in extracted_fields.items():
            print(f"{key}: {value}")
    else:
        print("Aucun texte n'a été extrait.")
