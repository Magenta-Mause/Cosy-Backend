package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.repositories.projections.GameServerPortUsage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GameServerRepository extends JpaRepository<GameServerEntity, String> {
    List<GameServerEntity> findByOwner_Uuid(String ownerUuid);

    /**
     * Every host port every game server has configured, one row per port.
     *
     * <p>Servers without port mappings are absent — they cannot collide with anything. The join
     * keeps this to a single flat query instead of loading whole entities with all their eager
     * collections just to read a port number.
     */
    @Query(
            """
            select new com.magentamause.cosybackend.repositories.projections.GameServerPortUsage(
                g.uuid, g.serverName, g.status, p.instancePort, p.protocol)
            from GameServerEntity g join g.portMappings p
            """)
    List<GameServerPortUsage> findAllPortUsages();

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
