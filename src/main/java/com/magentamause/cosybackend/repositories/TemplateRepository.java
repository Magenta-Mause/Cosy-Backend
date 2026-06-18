package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.TemplateEntity;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {

    @Query("SELECT DISTINCT t.gameId FROM TemplateEntity t")
    Set<Integer> findDistinctExternalGameIds();
}
