import cv2
import pytesseract
import re
import json
from datetime import datetime
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama.llms import OllamaLLM

# Configurer le chemin vers Tesseract
pytesseract.pytesseract.tesseract_cmd = r'/opt/homebrew/bin/tesseract'

# Fonction pour prétraiter l'image
def process_image(image_path):
    # Lire l'image
    image = cv2.imread(image_path)

    # Convertir en niveaux de gris
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    # Appliquer un seuil binaire
    _, thresh = cv2.threshold(gray, 150, 255, cv2.THRESH_BINARY)

    return thresh

# Fonction pour nettoyer le texte brut extrait
def clean_extracted_text(text):
    # Supprime les caractères inutiles
    text = re.sub(r"[^A-Za-z0-9À-ÿ\s'/،.-]", "", text).strip()

    # Remplace les espaces multiples par un seul espace
    text = re.sub(r"\s{2,}", " ", text)

    return text

# Fonction pour extraire les champs spécifiques
def extract_fields(text):
    fields = {}

    # Extraction du nom complet
    name_pattern = r"([A-ZÀ-ÿ']+\s+[A-ZÀ-ÿ']+)"
    name_match = re.search(name_pattern, text)
    if name_match:
        fields["Nom complet"] = name_match.group(1).strip()

    # Extraction de la date de naissance
    dob_pattern = r"(Née le|the|مزدادة بتاريخ)[:\s]*(\d{2}\.\d{2}\.\d{4}|\d{2}/\d{2}/\d{4})"
    dob_match = re.search(dob_pattern, text)
    if dob_match:
        fields["Date de naissance"] = dob_match.group(2).strip()

    # Extraction du lieu de naissance
    city_pattern = r"(à|ب)\s+([A-ZÀ-ÿ']+)"
    city_match = re.search(city_pattern, text)
    if city_match:
        fields["Lieu de naissance"] = city_match.group(2).strip()

    # Extraction du numéro de carte
    id_pattern = r"(\d{4,10})"
    id_match = re.search(id_pattern, text)
    if id_match:
        fields["Numéro de carte"] = id_match.group(1).strip()

    return fields

# Fonction pour améliorer et structurer les résultats avec Ollama
def improve_with_ollama(raw_text, extracted_fields):
    # Modèle Ollama et template de prompt
    template = """<s>[INST] <<SYS>>
You are a powerful assistant that extracts and structures information from identity cards. 
<</SYS>>[INST]
Prénom et nom (FR): John Wick
Prénom et nom (AR): جون ويك
Date de naissance: 12.09.1980
Lieu de naissance: Washington
CIN: FK9223 [/INST] Prénom et nom (FR),Prénom et nom (AR),Date de naissance,Lieu de naissance,CIN
John Wick,جون ويك,12.09.1980,Washington,FK9223
[INST]
{text}
[/INST]
"""

    prompt = ChatPromptTemplate.from_template(template)
    model = OllamaLLM(model="llama2")

    # Préparer la question pour le modèle Ollama
    question = "\n".join([f"{key}: {value}" for key, value in extracted_fields.items()])

    # Créer la chaîne (pipeline)
    conversation = prompt | model
    response = conversation.invoke({"text": raw_text + "\n" + question})

    return response

# Fonction principale pour effectuer l'OCR
def perform_ocr(image_path, language="fra"):
    try:
        # Prétraitement de l'image
        processed_image = process_image(image_path)

        # Configuration de Tesseract
        custom_config = r'--oem 3 --psm 6'
        raw_text = pytesseract.image_to_string(processed_image, config=custom_config, lang=language)

        # Nettoyer le texte extrait
        cleaned_text = clean_extracted_text(raw_text)

        # Extraire les champs spécifiques
        extracted_fields = extract_fields(cleaned_text)

        # Améliorer les résultats avec Ollama
        improved_result = improve_with_ollama(cleaned_text, extracted_fields)

        # Construire la réponse finale
        result = {
            "originalFileName": image_path.split("/")[-1],
            "size": f"{round(processed_image.size / 1024, 2)} KB",  # Taille de l'image
            "processedAt": datetime.now().isoformat(),
            "rawText": raw_text,
            "cleanedText": cleaned_text,
            "fields": extracted_fields,
            "improvedFields": improved_result
        }

        return result

    except Exception as e:
        return {"error": str(e)}

# Fonction pour afficher les résultats OCR
def display_ocr_results(ocr_result):
    print("\nRésultat OCR :")
    print(json.dumps(ocr_result, indent=4, ensure_ascii=False))

# Exemple d'utilisation
if __name__ == "__main__":
    image_path = "imaecin.jpeg"
    result = perform_ocr(image_path, language="fra+ara+eng")
    display_ocr_results(result)
