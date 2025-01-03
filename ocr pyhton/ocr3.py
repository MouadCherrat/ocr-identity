import requests

# Chemin de l'image à traiter
image_path = "zaineb ui.jpeg"
api_key = "K86289643888957"

# URL de l'API OCR.space
api_url = "https://api.ocr.space/parse/image"

# Préparer la requête
with open(image_path, "rb") as image_file:
    files = {"file": image_file}
    payload = {
        "apikey": api_key,
        "language": "fre",
        "isOverlayRequired": False,
    }

    # Envoyer la requête POST
    response = requests.post(api_url, data=payload, files=files)

# Traiter la réponse
if response.status_code == 200:
    result = response.json()
    if not result["IsErroredOnProcessing"]:
        extracted_text = result["ParsedResults"][0]["ParsedText"]
        print("Texte extrait :")
        print(extracted_text)
    else:
        print("Erreur dans le traitement OCR :", result["ErrorMessage"])
else:
    print("Erreur HTTP :", response.status_code)
