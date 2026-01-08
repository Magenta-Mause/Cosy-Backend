package com.magentamause.cosybackend.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerLogMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameServerLogWebsocketPublisher {

	private final SimpMessagingTemplate messagingTemplate;

	public void publishLog(String serverUuid, GameServerLogMessage logMessage) {
		messagingTemplate.convertAndSend("/topics/game-server-logs/creation/" + serverUuid, logMessage);
	}
}
