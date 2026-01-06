package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.services.GameServerService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game-server")
public class GameServerController {

    private final GameServerService gameServerService;

    @GetMapping("/{serviceName}/status")
    public ResponseEntity<String> getServiceInfo(@PathVariable String serviceName) {
        return ResponseEntity.ok(gameServerService.getStatus(serviceName));
    }

    @GetMapping(value = "/{serviceName}/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StartEventDto> startServiceSse(@PathVariable String serviceName) {
        Flux<StartEventDto> heartbeat =
                Flux.interval(Duration.ofSeconds(2)).map(tick -> StartEventDto.heartbeat());

        Mono<StartEventDto> work =
                Mono.fromCallable(() -> gameServerService.startServer(serviceName))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(StartEventDto::done)
                        .onErrorResume(ex -> Mono.just(StartEventDto.error(ex.getMessage())));

        return Flux.merge(heartbeat, work)
                .takeUntil(
                        event ->
                                event.getType().equals(StartEventDto.Type.DONE)
                                        || event.getType().equals(StartEventDto.Type.ERROR));
    }

    @PostMapping("/{serviceName}/stop")
    public ResponseEntity<Void> stopService(@PathVariable String serviceName) {
        gameServerService.stopServer(serviceName);
        return ResponseEntity.ok().build();
    }
}
