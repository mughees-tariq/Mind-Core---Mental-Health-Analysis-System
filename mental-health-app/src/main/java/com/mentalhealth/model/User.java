package com.mentalhealth.model;

import java.util.Objects;

// Domain model for an application user. Stored in the users table.
// Populated by UserRepository and passed around through Main.currentUser
// for the duration of the session.
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String name;
    private Integer age;
    private String goal;
    private int baselineStress;
    private String createdAt;
    private String feelingHistory;
    private String gender;
    private String location;
    private String affirmations;  // JSON array of affirmation strings
    private String securityQuestion;
    private String securityAnswer;

    public User() {}

    // Minimal constructor used at registration. Defaults baseline stress to 5.
    public User(String username, String passwordHash, String name) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.baselineStress = 5;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public int getBaselineStress() { return baselineStress; }
    public void setBaselineStress(int baselineStress) { this.baselineStress = baselineStress; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getFeelingHistory() { return feelingHistory; }
    public void setFeelingHistory(String feelingHistory) { this.feelingHistory = feelingHistory; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAffirmations() { return affirmations; }
    public void setAffirmations(String affirmations) { this.affirmations = affirmations; }

    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }

    public String getSecurityAnswer() { return securityAnswer; }
    // Trim on set to avoid whitespace mismatches when verifying the answer.
    public void setSecurityAnswer(String securityAnswer) {
        this.securityAnswer = securityAnswer != null ? securityAnswer.trim() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', name='" + name + "'}";
    }
}
