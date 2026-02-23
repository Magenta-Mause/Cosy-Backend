package com.magentamause.cosybackend.services.core.gameserver.webhookSender;

import com.magentamause.cosybackend.entities.GameServerEventType;

public record GameServerDomainEvent(
        String serverId, String serverName, GameServerEventType eventType) {}
