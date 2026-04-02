package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.module.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mobile")
@RequiredArgsConstructor
public class MobileModulesController {

    private final ModuleService moduleService;

    @GetMapping("/modules")
    public String modules(Model model) {
        try {
            Object modules = moduleService.getModules();
            model.addAttribute("modules", modules);
            System.out.println(modules);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("modules", new java.util.ArrayList<>());
        }
        return "modules/mobile-index";
    }
}
