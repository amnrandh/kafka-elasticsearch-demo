package org.example;

public class EnrichedProject {
    private String projectId;
    private String projectName;
    private String customerId;
    private String customerName;
    private double totalHours;

    public EnrichedProject(String projectId, String projectName, String customerId, String customerName, double totalHours) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.customerId = customerId;
        this.customerName = customerName;
        this.totalHours = totalHours;
    }

    // Getters and setters
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }

    @Override
    public String toString() {
        return "📁 Project: " + projectName +
                " (ID: " + projectId + ")" +
                " | 🧑 Customer: " + customerName +
                " (ID: " + customerId + ")" +
                " | 🕒 Total Hours: " + totalHours;
    }
}
