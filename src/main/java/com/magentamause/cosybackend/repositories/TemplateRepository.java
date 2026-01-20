package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {

}
