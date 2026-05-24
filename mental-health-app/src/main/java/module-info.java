module com.mentalhealth {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    
    requires java.sql;
    requires java.net.http;
    
    requires com.google.gson;
    
    opens com.mentalhealth to javafx.fxml;
    opens com.mentalhealth.controller to javafx.fxml;
    opens com.mentalhealth.model to javafx.fxml, com.google.gson;
    opens com.mentalhealth.service to com.google.gson;
    
    exports com.mentalhealth;
    exports com.mentalhealth.controller;
    exports com.mentalhealth.model;
    exports com.mentalhealth.service;
    exports com.mentalhealth.repository;
    exports com.mentalhealth.util;
}