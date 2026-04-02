package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.module.Module;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<Module, Long> {
}