package com.magentamause.cosybackend.services.gameserver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class GameServerLogService {
    private final WebClient lokiWebClient;


}
