package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeEntry {
    @JsonProperty("id")
    private int id;
    
    @JsonProperty("project_id")
    private String projectId;
    
    @JsonProperty("user_id")
    private int userId;
    
    @JsonProperty("hours_worked")
    private String hoursWorked;
    
    @JsonProperty("entry_date")
    private int entryDate;

    // Required no-arg constructor for Jackson
    public TimeEntry() {}

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public double getHoursWorked() {
        return Double.parseDouble(hoursWorked);
    }

    public void setHoursWorked(double hoursWorked) {
        this.hoursWorked = Double.toString(hoursWorked);
    }

    public int getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(int entryDate) {
        this.entryDate = entryDate;
    }

    @Override
    public String toString() {
        return "TimeEntry{" +
               "id=" + id +
               ", projectId=" + projectId +
               ", userId=" + userId +
               ", hoursWorked=" + hoursWorked +
               ", entryDate=" + entryDate +
               '}';
    }
}

