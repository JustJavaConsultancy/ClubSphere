package com.justjava.mycommunity.chat.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class SessionDTO {
    private Long id;
    private String sessionType;
    private String videoLink;
    private String moduleName;
    private String description;
    private String startDate;
    private String startTime;
    private Long duration;
    private boolean hasCertificate;
    private String certificateHtml;
    private int numberOfParticipants;
    private String status;
    private List<String> participantNames;


    public void setStatus() {
        // If status is already set, return it
        if (this.status != null && !this.status.isEmpty()) {
            return;
        }

        // Calculate status dynamically if not set
        try {
            if (startDate == null || startDate.isEmpty()) {
                this.status = "Unknown";
            }

            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate sessionDate = LocalDate.parse(startDate, formatter);

            if (sessionDate.isBefore(currentDate)) {
                this.status = "Completed";
            } else if (sessionDate.isEqual(currentDate) || sessionDate.isAfter(currentDate)) {
                this.status = "Upcoming";
            } else {
                this.status = "Unknown";
            }
        } catch (Exception e) {
            System.err.println("Error calculating status for session " + id + ": " + e.getMessage());
            this.status = "Upcoming"; // Default to Upcoming to ensure events show up
        }
    }

    public boolean isHasCertificates() {
        return hasCertificate;
    }

    public String sessionType(){
        return sessionType;
    }
    public String videoLink(){
        return videoLink;
    }

    public boolean isCompleted() {
        return "Completed".equalsIgnoreCase(this.status);
    }
}
