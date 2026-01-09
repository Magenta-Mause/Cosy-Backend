package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.entities.GameServerLogMessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerLogWebsocketPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public void publishLog(String serverUuid, GameServerLogMessageEntity logMessage) {
		messagingTemplate.convertAndSend("/topics/game-server-logs/creation/" + serverUuid, logMessage);
	}
}
