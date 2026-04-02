package com.justjava.mycommunity.posts;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum PostLevel {
    COMMUNITY("COMMUNITY"),
    GROUP("GROUP"),
    GENERAL("GENERAL");

    private final String value;

    PostLevel(String value){
        this.value = value;
    }

    public static PostLevel of(String value){
        return Arrays.stream(PostLevel.values())
                .filter(p -> p.getValue().equalsIgnoreCase(value))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid PostLevel: " + value));
    }
}
