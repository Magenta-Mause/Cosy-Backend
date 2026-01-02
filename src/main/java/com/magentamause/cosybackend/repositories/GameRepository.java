package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.GameEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<GameEntity, Integer> {

    @Query("SELECT g FROM GameEntity g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<GameEntity> findByNameContainingIgnoreCase(@Param("query") String query);

    Optional<GameEntity> findById(int id);

    default GameEntity saveIfNotPresent(GameEntity entity) {
        return findById(entity.getId()).orElseGet(() -> save(entity));
    }
}
