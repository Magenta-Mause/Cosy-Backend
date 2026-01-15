package com.magentamause.cosybackend.dtos.websockets;

import com.magentamause.cosybackend.dtos.entitydtos.PullProgressDto;

public record GameServerDockerProgressUpdateDto(String serverUuid, PullProgressDto progress) {}
