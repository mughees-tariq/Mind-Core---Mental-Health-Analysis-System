package com.mentalhealth.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;

// Controller for AboutScreen.fxml. Static info page; no data fetched.
public class AboutController extends BaseController {

    @FXML private BorderPane root;

    @Override
    protected BorderPane getRoot() { return root; }

    @FXML
    public void initialize() {
        animateCards();
    }
}
