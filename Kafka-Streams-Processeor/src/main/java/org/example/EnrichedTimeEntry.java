package org.example;

public class EnrichedTimeEntry {
    private TimeEntry timeEntry;
    private Project project;
    private Customer customer;

    public EnrichedTimeEntry(TimeEntry timeEntry, Project project, Customer customer) {
        this.timeEntry = timeEntry;
        this.project = project;
        this.customer = customer;
    }

    @Override
    public String toString() {
        return "EnrichedTimeEntry{" +
                "timeEntry=" + timeEntry +
                ", project=" + project +
                ", customer=" + customer +
                '}';
    }
}
