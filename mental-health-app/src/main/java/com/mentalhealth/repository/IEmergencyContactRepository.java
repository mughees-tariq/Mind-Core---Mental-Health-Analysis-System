package com.mentalhealth.repository;

import com.mentalhealth.model.EmergencyContact;
import java.util.List;

// Contract for emergency contact persistence operations
public interface IEmergencyContactRepository {

    // Persists a new emergency contact; returns true if succeeded
    boolean save(EmergencyContact contact);

    // Returns all emergency contacts for user, ordered by creation date
    List<EmergencyContact> findByUserId(int userId);

    // Deletes emergency contact by ID; returns true if succeeded
    boolean delete(int contactId);
}
