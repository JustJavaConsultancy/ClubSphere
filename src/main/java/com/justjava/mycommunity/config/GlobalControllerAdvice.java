package com.justjava.mycommunity.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("currentPath")
    public String getCurrentPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        System.out.println("Current Path: " + path); // 👈 prints to console/logs
        return path;
    }
}
