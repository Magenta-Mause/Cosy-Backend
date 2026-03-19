package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.CosyInstanceSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CosyInstanceSettingsRepository
        extends JpaRepository<CosyInstanceSettingsEntity, Long> {}
