package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class GameServerStatusUpdateDto {
    private final String serverUuid;
    private final GameServerDto.GameServerStatus newStatus;
}
