package com.justjava.mycommunity.module;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ModulesController {
    
    private final ModuleService moduleService;

    @GetMapping("/modules")
    public String modules(Model model) {
        try {
            Object modules = moduleService.getModules();
            model.addAttribute("modules", modules);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("modules", new java.util.ArrayList<>());
        }
        return "modules/index";
    }
}


