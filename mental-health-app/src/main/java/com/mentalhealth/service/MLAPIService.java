package com.mentalhealth.service;

import com.mentalhealth.model.Prediction;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

// HTTP client for the local Flask ML API running on localhost:5000.
// Called by AnalysisController (predict) and MoodTrackerController (generateReport).
// Returns structured result objects so callers never touch raw JSON.
public class MLAPIService implements IMLAPIService {

    private static final Logger LOG = Logger.getLogger(MLAPIService.class.getName());

    private static final String API_URL = "http://localhost:5000";
    private final HttpClient httpClient;
    private final Gson gson;

    public MLAPIService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    // GET /health — returns true only when the model is loaded.
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/health"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.get("model_loaded").getAsBoolean();
            }
            return false;

        } catch (Exception e) {
            LOG.warning("API Health Check Failed: " + e.getMessage());
            return false;
        }
    }

    // POST /predict — sends the text and parses the top-N class probabilities.
    public PredictionResult predict(String text) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("text", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                if (json.get("success").getAsBoolean()) {
                    PredictionResult result = new PredictionResult();
                    result.setSuccess(true);
                    result.setPrediction(json.get("prediction").getAsString());
                    result.setConfidence(json.get("confidence").getAsDouble());
                    result.setSeverity(json.get("severity").getAsString());
                    result.setColor(json.get("color").getAsString());

                    JsonArray topPredictions = json.getAsJsonArray("top_predictions");
                    TopPrediction[] topPreds = new TopPrediction[topPredictions.size()];

                    for (int i = 0; i < topPredictions.size(); i++) {
                        JsonObject pred = topPredictions.get(i).getAsJsonObject();
                        topPreds[i] = new TopPrediction(
                                pred.get("class").getAsString(),
                                pred.get("probability").getAsDouble(),
                                pred.get("severity").getAsString(),
                                pred.get("color").getAsString()
                        );
                    }
                    result.setTopPredictions(topPreds);

                    return result;
                }
            }

            PredictionResult error = new PredictionResult();
            error.setSuccess(false);
            error.setError("API returned error: " + response.statusCode());
            return error;

        } catch (Exception e) {
            LOG.warning("Prediction Failed: " + e.getMessage());
            PredictionResult error = new PredictionResult();
            error.setSuccess(false);
            error.setError("Connection failed: " + e.getMessage());
            return error;
        }
    }

    // POST /generate-report — sends the mood history to Gemini AI and returns
    // a human-readable wellness summary.
    public ReportResult generateReport(String userName, List<Prediction> predictions) {
        try {
            JsonArray historyArray = new JsonArray();
            java.time.ZoneId pkt = java.time.ZoneId.of("Asia/Karachi");
            java.time.format.DateTimeFormatter isoFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            for (Prediction p : predictions) {
                JsonObject day = new JsonObject();
                try {
                    java.time.LocalDateTime utc = java.time.LocalDateTime.parse(p.getCreatedAt().substring(0, 19),
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String pkisoDate = utc.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(pkt).format(isoFmt);
                    day.addProperty("date", pkisoDate);
                } catch (Exception e) {
                    day.addProperty("date", p.getCreatedAt());
                }
                day.addProperty("prediction", p.getPrediction());
                day.addProperty("confidence", p.getConfidence());
                day.addProperty("input_text", p.getInputText());
                day.addProperty("severity", p.getSeverity());
                historyArray.add(day);
            }

            JsonObject requestBody = new JsonObject();
            requestBody.add("mood_history", historyArray);
            requestBody.addProperty("user_name", userName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/generate-report"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                if (json.get("success").getAsBoolean()) {
                    ReportResult result = new ReportResult();
                    result.setSuccess(true);
                    result.setReport(json.get("report").getAsString());
                    result.setGeneratedAt(json.get("generated_at").getAsString());

                    JsonObject summary = json.getAsJsonObject("summary");
                    result.setDominantMood(summary.get("dominant_mood").getAsString());
                    result.setTotalEntries(summary.get("total_entries").getAsInt());
                    result.setPeriod(summary.get("period").getAsString());

                    return result;
                }
            }

            ReportResult error = new ReportResult();
            error.setSuccess(false);
            error.setError("API returned error: " + response.statusCode());
            return error;

        } catch (Exception e) {
            LOG.warning("Report Generation Failed: " + e.getMessage());
            ReportResult error = new ReportResult();
            error.setSuccess(false);
            error.setError("Connection failed: " + e.getMessage());
            return error;
        }
    }

    // Result wrapper for a single /predict call.
    public static class PredictionResult {
        private boolean success;
        private String prediction;
        private double confidence;
        private String severity;
        private String color;
        private String error;
        private TopPrediction[] topPredictions;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getPrediction() { return prediction; }
        public void setPrediction(String prediction) { this.prediction = prediction; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public TopPrediction[] getTopPredictions() { return topPredictions; }
        public void setTopPredictions(TopPrediction[] topPredictions) {
            this.topPredictions = topPredictions;
        }

        public double getConfidencePercent() {
            return confidence * 100;
        }
    }

    // One entry in the top-N class breakdown returned by /predict.
    public static class TopPrediction {
        private String className;
        private double probability;
        private String severity;
        private String color;

        public TopPrediction(String className, double probability, String severity, String color) {
            this.className = className;
            this.probability = probability;
            this.severity = severity;
            this.color = color;
        }

        public String getClassName() { return className; }
        public double getProbability() { return probability; }
        public double getProbabilityPercent() { return probability * 100; }
        public String getSeverity() { return severity; }
        public String getColor() { return color; }
    }

    // Result wrapper for a /generate-report call.
    public static class ReportResult {
        private boolean success;
        private String report;
        private String error;
        private String generatedAt;
        private String dominantMood;
        private int totalEntries;
        private String period;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getReport() { return report; }
        public void setReport(String report) { this.report = report; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

        public String getDominantMood() { return dominantMood; }
        public void setDominantMood(String dominantMood) { this.dominantMood = dominantMood; }

        public int getTotalEntries() { return totalEntries; }
        public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
    }
}
