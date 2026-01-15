package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class GameServerDockerProgressUpdateDto {
    private final String serverUuid;
    private final PullProgressDto progress;
}
