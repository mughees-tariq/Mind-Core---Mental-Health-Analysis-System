package com.mentalhealth.model;

import java.util.Objects;

// An emergency contact belonging to a user. Stored in emergency_contacts table.
// Displayed in ProfileController; managed via EmergencyContactService.
public class EmergencyContact {
    private int id;
    private int userId;
    private String name;
    private String phone;
    private String relationship;
    private String createdAt;

    public EmergencyContact() {}

    public EmergencyContact(int userId, String name, String phone, String relationship) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmergencyContact that = (EmergencyContact) o;
        return id == that.id && userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "EmergencyContact{id=" + id + ", name='" + name + "', phone='" + phone + "'}";
    }
}
