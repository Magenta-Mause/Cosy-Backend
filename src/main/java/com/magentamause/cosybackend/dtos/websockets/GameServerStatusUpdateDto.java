package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;

public record GameServerStatusUpdateDto(String serverUuid, GameServerDto.GameServerStatus newStatus) {
}
