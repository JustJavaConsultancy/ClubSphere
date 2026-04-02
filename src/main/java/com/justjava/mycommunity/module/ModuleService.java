package com.justjava.mycommunity.module;

import com.justjava.mycommunity.chat.dto.CreateModuleDTO;
import com.justjava.mycommunity.chat.dto.ModuleDTO;
import com.justjava.mycommunity.chat.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleRepository moduleRepository;

    public void createModule(CreateModuleDTO dto){

        Module module = new Module();
        module.setName(dto.getName());
        module.setDescription(dto.getDescription());
        module.setPrice(dto.getPrice());
        if (dto.getFile() != null)
            module.setFiles(dto.getFile());
        moduleRepository.save(module);

    }

    public Object getModules() {
        List<Module> modules = moduleRepository.findAll();
        List<ModuleDTO> moduleDTOs = new ArrayList<>();
        for (Module module : modules) {
            ModuleDTO dto = new ModuleDTO();
            dto.setId(module.getId());
            dto.setName(module.getName());
            dto.setDescription(module.getDescription());
            dto.setPrice(module.getPrice());
            moduleDTOs.add(dto);
        }
        return moduleDTOs;
    }
}
