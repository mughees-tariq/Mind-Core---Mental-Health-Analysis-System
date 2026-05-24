package com.mentalhealth.service;

import com.mentalhealth.model.Prediction;
import com.mentalhealth.service.MLAPIService.*;
import java.util.List;

// Contract for the Flask ML API client. Implemented by MLAPIService.
public interface IMLAPIService {

    // GET /health — true if the API is up and the model is loaded.
    boolean isHealthy();

    // POST /predict — returns the mental-health prediction for the given text.
    PredictionResult predict(String text);

    // POST /generate-report — returns an AI-written summary of the mood history.
    ReportResult generateReport(String userName, List<Prediction> predictions);
}
