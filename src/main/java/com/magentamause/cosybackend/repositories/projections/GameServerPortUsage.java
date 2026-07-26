package com.magentamause.cosybackend.repositories.projections;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;

/**
 * One host port claimed by one game server — everything a port collision check needs to know about
 * a server, and nothing else.
 *
 * <p>Loading whole {@code GameServerEntity} instances for this would drag in every eagerly mapped
 * collection (layouts, webhooks, access groups, mounts) of every server on the instance, on every
 * save and every start.
 *
 * @param gameServerUuid owner of the claim
 * @param serverName name of the claiming server, for server-side logs
 * @param status the claiming server's status; only a server that is not stopped actually holds its
 *     ports
 */
public record GameServerPortUsage(
        String gameServerUuid,
        String serverName,
        GameServerDto.GameServerStatus status,
        int instancePort,
        PortMapping.PortProtocol protocol) {}
