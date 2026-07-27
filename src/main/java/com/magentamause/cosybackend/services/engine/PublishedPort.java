package com.magentamause.cosybackend.services.engine;

import com.magentamause.cosybackend.entities.gameserver.utility.PortMapping;

/**
 * A host port that is currently bound by the engine.
 *
 * <p>Covers every container the engine reports, not just the ones Cosy manages — a port taken by an
 * unrelated container blocks a game server just as effectively as one taken by a Cosy server.
 *
 * @param port the port occupied on the host
 * @param protocol the protocol the binding uses; TCP and UDP bindings of the same port number do
 *     not conflict with each other
 * @param containerName name of the occupying container, for server-side logging
 * @param gameServerUuid uuid of the occupying Cosy game server, or {@code null} when the container
 *     is not managed by this Cosy instance
 */
public record PublishedPort(
        int port, PortMapping.PortProtocol protocol, String containerName, String gameServerUuid) {

    public boolean belongsToGameServer(String uuid) {
        return gameServerUuid != null && gameServerUuid.equals(uuid);
    }
}
