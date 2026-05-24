package com.mentalhealth.service;

import com.mentalhealth.model.EmergencyContact;
import com.mentalhealth.repository.EmergencyContactRepository;
import com.mentalhealth.repository.IEmergencyContactRepository;

import java.util.List;

// Thin service for emergency contact CRUD. Used by ProfileController.
// Delegates to EmergencyContactRepository which writes to emergency_contacts table.
public class EmergencyContactService {
    private final IEmergencyContactRepository repository;

    public EmergencyContactService() {
        this.repository = new EmergencyContactRepository();
    }

    public EmergencyContactService(IEmergencyContactRepository repository) {
        this.repository = repository;
    }

    public List<EmergencyContact> findByUserId(int userId) {
        return repository.findByUserId(userId);
    }

    public boolean save(EmergencyContact contact) {
        return repository.save(contact);
    }

    public boolean delete(int contactId) {
        return repository.delete(contactId);
    }
}
