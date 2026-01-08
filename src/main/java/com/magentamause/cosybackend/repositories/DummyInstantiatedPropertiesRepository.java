package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.DummyInstantiatedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DummyInstantiatedPropertiesRepository
        extends JpaRepository<DummyInstantiatedEntity, String> {}
