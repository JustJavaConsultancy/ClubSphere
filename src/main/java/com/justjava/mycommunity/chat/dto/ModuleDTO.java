package com.justjava.mycommunity.chat.dto;

import com.justjava.mycommunity.module.Module;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for {@link Module}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ModuleDTO implements Serializable {
    Long id;
    String name;
    String description;
    Double price;
}