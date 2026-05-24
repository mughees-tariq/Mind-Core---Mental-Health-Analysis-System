# MindCore – Mental Health Analysis App 🧠

*Type how you feel. Let the model do the rest.*

MindCore is a desktop mental health analysis application built with JavaFX and a Python/Flask ML backend. It classifies your emotional state from free-text journal entries using a fine-tuned DistilBERT model, tracks your mood history over time, and generates a personalised AI consoling report — all running locally on your machine.

---

## 🌐 Live Model Demo

Try the mental health classifier instantly in your browser — no installation needed:

**👉 [MindCore on Hugging Face Spaces](https://huggingface.co/spaces/mughees-tariq/mindcore)**

---

## 📸 Screenshots

![Login Screen](Images/loginScreen.png)
*Secure login with bcrypt password hashing*

![Register Screen](Images/registerScreen.png)
*Account registration with input validation*

![Register Screen](Images/registerScreen2.png)
*Account registration with optional details*

![Dashboard](Images/dashboardScreen.png)
*Streak tracker, self-care checklist, and notifications*

![Analysis Screen](Images/analysisScreen.png)
*DistilBERT text classification with top-3 confidence breakdown*

![Mood Tracker](Images/moodTrackerScreen.png)
*Mood line chart, trend analysis, and entry history*

![Mood Tracker](Images/moodTrackerScreen2.png)
*AI report generation*

![History Screen](Images/historyScreen.png)
*Full prediction history with advanced filtering and bulk export*

![Recommendations](Images/recommendationsScreen.png)
*Personalised self-care recommendations based on mental state*

![Recommendations](Images/recommendationsScreen2.png)
*Weekly progress tracker*

![Profile Screen](Images/profileScreen.png)
*Profile management, personal information, wellness statistics, and mood activity*

![Profile Screen](Images/profileScreen2.png)
*Analytics summary, weekly report card, and goals & affirmations*

![Profile Screen](Images/profileScreen3.png)
*Emergency contacts, export data, and account management*

---

## 🏗️ Architecture Overview

MindCore runs as **two programs** working in tandem:

- **Python Flask API** (`python-api/`) — Loads a fine-tuned DistilBERT model locally and exposes two endpoints: `/predict` for mental health classification and `/generate-report` for AI-generated consoling mood reports via the Hugging Face Inference API.
- **JavaFX Desktop App** (`mental-health-app/`) — The full UI application built with JavaFX 23. Communicates with the Flask API over localhost, persists all user data in a local SQLite database, and handles auth, mood tracking, history, recommendations, and profile management.

---

## 🛠 Features

### 🤖 ML-Powered Analysis

- **DistilBERT text classification** — Fine-tuned on a mental health dataset; classifies input text into 7 categories: Normal, Stress, Anxiety, Depression, Bipolar, Personality Disorder, and Suicidal
- **Severity scoring** — Each prediction is tagged Low / Moderate / High / Critical
- **Top-3 confidence breakdown** — Shows the top 3 candidate diagnoses with confidence percentages alongside every prediction
- **Smart negation detection** — A custom NLP pre-processing layer handles negated phrases (e.g. "I don't feel anxious") and idiomatic language before passing text to the model
- **AI Consoling Report** — Generates a personalised, empathetic mood report based on your full history using Hugging Face's Inference API (requires free HF token)

### 📊 Dashboard & Mood Tracker

- **Streak tracker** — Tracks consecutive days of mood logging; highlights 7-day streaks
- **Mood line chart** — Interactive JavaFX `LineChart` showing mood scores over time with an average reference line
- **Trend analysis** — Automatically labels your recent mood trend as 📈 Improving, 📉 Declining, or ➡️ Stable
- **Analytics cards** — Best day, worst day, average score, and mood stability index
- **Self-care checklist** — Daily self-care tasks persisted in SQLite; resets each day
- **In-app notifications** — Bell icon with badge count for reminders and alerts

### 🗂️ History & Filtering

- **Full prediction history** — Every analysis entry stored with timestamp, input text, prediction, confidence, severity, tags, and notes
- **Advanced filtering** — Filter by date range, mood type, severity level, confidence range, starred, or keyword search
- **Multiple views** — Toggle between List View, Timeline View, and Compare View
- **Bulk actions** — Star, delete, or export multiple entries at once
- **CSV & JSON export** — Export your full history or current filtered view with or without notes

### 👤 User Management

- **Secure auth** — Register/login with bcrypt-hashed passwords and input validation
- **Forgot password** — Security question-based password recovery
- **Profile management** — Edit display name, email, security question, and emergency contacts
- **Multi-user support** — Each account has its own isolated SQLite-backed history

### 💊 Recommendations

- **Personalised self-care recommendations** — Based on your predicted mental health state
- **Progress tracking** — Tracks recommendation completion across sessions

---

## 📁 Project Structure

```
MindCore-Mental-Health-Analysis/
│
├── python-api/
│   ├── app.py                  # Flask API — /predict and /generate-report endpoints
│   ├── model.safetensors       # Fine-tuned DistilBERT weights
│   ├── tokenizer.json          # Tokenizer config
│   ├── config.json             # Model architecture config
│   ├── classes.npy             # Label classes array
│   ├── requirements.txt        # Python dependencies
│   └── .env                    # HF_API_TOKEN goes here (see setup below)
│
├── mental-health-app/
│   ├── src/main/java/com/mentalhealth/
│   │   ├── Main.java
│   │   ├── controller/         # JavaFX controllers for each screen
│   │   ├── service/            # Business logic (Auth, Dashboard, ML API, etc.)
│   │   ├── repository/         # SQLite data access layer
│   │   ├── model/              # Data models (User, MoodEntry, Prediction, etc.)
│   │   └── util/               # Helpers (validation, password hashing, animations)
│   ├── src/main/resources/
│   │   ├── fxml/               # UI layout files for all screens
│   │   └── css/styles.css      # App-wide stylesheet
│   ├── data/mental_health.db   # SQLite database
│   └── pom.xml                 # Maven build config
│
├── run.ps1                     # Full launcher script (auto-installs, starts both programs)
└── run.bat                     # Double-click entry point — calls run.ps1
```

---

## ⚙️ Prerequisites

- **Windows** (the launcher scripts are Windows-only)
- **Java 21+** — [Download here](https://adoptium.net/)
- **Apache Maven** — [Download here](https://maven.apache.org/download.cgi) — must be on your system `PATH`
- **Python 3.10+** — [Download here](https://www.python.org/downloads/) — must be on your system `PATH`
- A free **Hugging Face account** for the AI Report feature (optional but recommended)

---

## 🔑 Hugging Face API Key Setup (for AI Report Generation)

The Generate Report feature calls the Hugging Face Inference API. It's completely free — just follow these steps:

1. Go to [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)
2. Click **New token** → give it any name → set role to **Read** → click **Create token**
3. Copy the token (starts with `hf_...`)
4. Open `python-api/.env` in any text editor
5. Replace the placeholder:
```
HF_API_TOKEN=your_huggingface_token_here
```
with your actual token:
```
HF_API_TOKEN=hf_xxxxxxxxxxxxxxxxxxxxxxxx
```
6. Save the file — you're done

> The app works fully without this token. Only the **Generate Report** button on the Mood Tracker screen requires it.

---

## 🚀 Running the App

### One-click launch (Recommended)

Simply double-click **`run.bat`** in the root folder.

That's it. The launcher will:
- Automatically create a Python virtual environment on first run
- Install all Python dependencies including PyTorch (CPU) — **this takes a few minutes the first time**
- Start the Flask API in the background
- Wait for the ML model to load (~10 seconds)
- Launch the JavaFX app

On every subsequent run it detects the existing environment and starts in just a few seconds.

### Manual launch (Advanced)

If you prefer to run the two programs separately:

**Terminal 1 — Start the Python API:**
```bash
cd python-api
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

**Terminal 2 — Start the Java app** (once the API is running):
```bash
cd mental-health-app
mvn clean compile javafx:run
```

---

## 🖥️ App Screens

| Screen | Description |
|---|---|
| Login / Register | Secure auth with bcrypt password hashing |
| Dashboard | Streak, self-care checklist, notifications, quick stats |
| Analysis | Free-text input → DistilBERT prediction + confidence breakdown |
| Mood Tracker | Line chart, trend analysis, AI report generation |
| History | Full entry log with advanced filters and CSV/JSON export |
| Recommendations | Personalised self-care tips based on your mental state |
| Profile | Edit account details and manage emergency contacts |

---

## 📦 Tech Stack

| Layer | Technology |
|---|---|
| Desktop UI | JavaFX 23, FXML, CSS |
| Build | Apache Maven |
| Database | SQLite (via sqlite-jdbc 3.45.1) |
| ML Model | DistilBERT (fine-tuned, 7-class classification) |
| ML Runtime | Python 3.10+, PyTorch (CPU), HuggingFace Transformers |
| API Server | Flask 3.1, Flask-CORS |
| AI Reports | Hugging Face Inference API |
| Live Demo | Hugging Face Spaces |
| Launcher | PowerShell + Batch |

---

## 👨‍💻 Developers

**Muhammad Mughees Tariq Khawaja** — [LinkedIn](https://linkedin.com/in/mugheestariq)

---

## 📜 License & Acknowledgments

Developed as a semester project for **CS 3004 - Software Design and Analysis** at FAST-NUCES, Spring 2026. The DistilBERT model was fine-tuned on a publicly available mental health text dataset for academic purposes.
