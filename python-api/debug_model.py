import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np, torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

model_dir = os.path.dirname(os.path.abspath(__file__))
tokenizer = AutoTokenizer.from_pretrained(model_dir)
model = AutoModelForSequenceClassification.from_pretrained(model_dir)
model.eval()
classes = np.load(os.path.join(model_dir, "classes.npy"), allow_pickle=True)

from app import preprocess_text, analyze_text_signals, adjust_probabilities

text = "I wish I was never born, I feel like a burden to everyone"
processed = preprocess_text(text)
print(f"Original:     {text}")
print(f"Preprocessed: {processed}")

inputs = tokenizer(processed, return_tensors="pt", truncation=True, padding=True, max_length=512)
with torch.no_grad():
    probs = torch.softmax(model(**inputs).logits, dim=1)[0].numpy()

print("\n--- Raw model probabilities ---")
for i, cls in enumerate(classes):
    print(f"  {str(cls):25s} {probs[i]:.4f} ({probs[i]*100:.1f}%)")

scores, negated, emphasis = analyze_text_signals(text, processed)
print(f"\n--- analyze_text_signals ---")
print(f"  Keyword scores: {scores}")
print(f"  Negated classes: {negated}")
print(f"  Emphasis: {emphasis}")

adjusted = adjust_probabilities(probs, classes, text, preprocessed_text=processed)
print("\n--- Adjusted probabilities ---")
for i, cls in enumerate(classes):
    print(f"  {str(cls):25s} {adjusted[i]:.4f} ({adjusted[i]*100:.1f}%)")

predicted = str(classes[np.argmax(adjusted)])
print(f"\nPrediction: {predicted} ({adjusted[np.argmax(adjusted)]*100:.1f}%)")
