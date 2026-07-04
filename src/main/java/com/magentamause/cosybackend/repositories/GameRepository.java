package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.GameEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<GameEntity, String> {

    @Query("SELECT g FROM GameEntity g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<GameEntity> queryByName(@Param("query") String query);

    Optional<GameEntity> findByExternalGameId(int externalGameId);

    Optional<GameEntity> findBySlug(String slug);

    List<GameEntity> findByExternalGameIdIn(List<Integer> externalGameIds);

    List<GameEntity> findBySlugIn(List<String> slugs);

    default GameEntity saveIfNotPresent(GameEntity entity) {
        return findByExternalGameId(entity.getExternalGameId()).orElseGet(() -> save(entity));
    }
}
