package com.justjava.mycommunity.chat.dto;

import lombok.Data;

@Data
public class CreateModuleDTO {

    private String name;
    private String description;
    private Double price;
    private byte[] file;

    public CreateModuleDTO(String name, String description, Double price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }
}
