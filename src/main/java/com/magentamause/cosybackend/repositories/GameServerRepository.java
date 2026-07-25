package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GameServerRepository extends JpaRepository<GameServerEntity, String> {
    List<GameServerEntity> findByOwner_Uuid(String ownerUuid);

    /**
     * Writes the status of an existing game server and reports how many rows were affected.
     *
     * <p>{@code save()} cannot be used for this: the entity has an assigned {@code @Id} and no
     * {@code @Version}, so Hibernate resolves it to a {@code merge()}. If the row was deleted
     * before merge's SELECT, the detached entity is treated as transient and re-INSERTed — a
     * deleted game server would silently reappear. A conditional update simply affects zero rows
     * instead.
     *
     * @return the number of updated rows, {@code 0} when the server no longer exists
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update GameServerEntity g set g.status = :status where g.uuid = :uuid")
    int updateStatusIfPresent(
            @Param("uuid") String uuid, @Param("status") GameServerDto.GameServerStatus status);
}
