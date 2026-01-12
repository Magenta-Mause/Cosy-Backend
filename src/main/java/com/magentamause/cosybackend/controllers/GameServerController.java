package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.GameServerCreationDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.dtos.entitydtos.GameServerStatusDto;
import com.magentamause.cosybackend.dtos.entitydtos.StartEventDto;
import com.magentamause.cosybackend.entities.GameServerEntity;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.RequireAccess;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.ResourceId;
import com.magentamause.cosybackend.services.auth.SecurityContextFilter;
import com.magentamause.cosybackend.services.gameserver.GameServerService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
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
    private final SecurityContextFilter securityContextFilter;

    @GetMapping
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<List<GameServerDto>> getAllGameServers() {
        List<GameServerDto> dtos =
                gameServerService.getAllGameServers().stream()
                        .map(GameServerEntity::toDto)
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{uuid}")
    @RequireAccess(action = Action.READ, resource = Resource.GAME_SERVER)
    public ResponseEntity<GameServerDto> getGameServerById(@PathVariable @ResourceId String uuid) {
        GameServerEntity entity = gameServerService.getGameServerById(uuid);
        return ResponseEntity.ok(entity.toDto());
    }

    @DeleteMapping("/{uuid}")
    @RequireAccess(action = Action.DELETE, resource = Resource.GAME_SERVER)
    public ResponseEntity<Void> deleteGameServerById(@PathVariable @ResourceId String uuid) {
        gameServerService.deleteGameServerById(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @RequireAccess(action = Action.CREATE, resource = Resource.GAME_SERVER)
    public ResponseEntity<GameServerDto> createGameServer(
            @Valid @RequestBody GameServerCreationDto gameServerCreationDto) {
        UserEntity user = securityContextFilter.getUser();

        GameServerEntity createdGameServer =
                gameServerService.convertDtoToEntity(gameServerCreationDto);
        createdGameServer.setOwner(user);

        gameServerService.saveGameServer(createdGameServer);
        return ResponseEntity.status(201).body(createdGameServer.toDto());
    }

    @GetMapping("/{uuid}/status")
    public ResponseEntity<GameServerStatusDto> getServiceInfo(@PathVariable String uuid) {
        return ResponseEntity.ok(gameServerService.getStatus(uuid));
    }

    @PostMapping(value = "/{uuid}/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StartEventDto> startServiceSse(@PathVariable String uuid) {
        Flux<StartEventDto> heartbeat =
                Flux.interval(Duration.ofSeconds(2)).map(tick -> StartEventDto.heartbeat());

        Mono<StartEventDto> work =
                Mono.fromCallable(() -> gameServerService.startServer(uuid))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(StartEventDto::done)
                        .onErrorResume(ex -> Mono.just(StartEventDto.error(ex.getMessage())));

        return Flux.merge(heartbeat, work)
                .takeUntil(
                        event ->
                                event.getType().equals(StartEventDto.Type.DONE)
                                        || event.getType().equals(StartEventDto.Type.ERROR));
    }

    @PostMapping("/{uuid}/stop")
    public ResponseEntity<Void> stopService(@PathVariable String uuid) {
        gameServerService.stopServer(uuid);
        return ResponseEntity.ok().build();
    }
}
