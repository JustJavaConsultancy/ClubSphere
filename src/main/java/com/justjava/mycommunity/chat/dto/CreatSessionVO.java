package com.justjava.mycommunity.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data

public class CreatSessionVO {

    @NotNull(message = "Module is required")
    private Long moduleId;

    private String sessionType;

    private String videoLink;

    private String description;

    private boolean hasCertificate;

    private String certificateHtml;

    @NotBlank(message = "Start date is required")
    private String startDate;

    @NotBlank(message = "Start time is required")
    private String startTime;

    // Mark optional if your form doesn’t include them yet
    private String endDate;

    private String endTime;

    @NotNull(message = "Duration is required")
    private Long duration;

    private List<String> users = new ArrayList<>();

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isHasCertificate() {
        return hasCertificate;
    }

    public void setHasCertificate(boolean hasCertificate) {
        this.hasCertificate = hasCertificate;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return "CreatSessionVO{" +
                "moduleId=" + moduleId +
                "sessionType='" + sessionType + '\'' +
                ", videoLink='" + videoLink + '\'' +
                ", description='" + description + '\'' +
                ", hasCertificate=" + hasCertificate +
                "certificateHtml='" + certificateHtml + '\'' +
                ", startDate='" + startDate + '\'' +
                ", startTime='" + startTime + '\'' +
                ", endDate='" + endDate + '\'' +
                ", endTime='" + endTime + '\'' +
                ", duration=" + duration +
                ", users=" + users +
                '}';
    }

    public boolean getHasCertificate() {
        return hasCertificate;
    }

    public void setCertificateHtml(String certificateHtml) {
        this.certificateHtml = certificateHtml;
    }
}
