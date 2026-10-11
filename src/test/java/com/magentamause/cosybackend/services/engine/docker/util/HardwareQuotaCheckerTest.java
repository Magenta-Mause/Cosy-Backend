package com.magentamause.cosybackend.services.engine.docker.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magentamause.cosybackend.dtos.entitydtos.GameServerDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.gameserver.GameServerEntity;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.exceptions.ErrorCode;
import com.magentamause.cosybackend.exceptions.HardwareLimitException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HardwareQuotaCheckerTest {

    private final HardwareQuotaChecker checker = new HardwareQuotaChecker();

    @Test
    void startWithinTheQuotaPasses() {
        UserEntity user = userWithLimits(4.0, "8GiB");
        GameServerEntity running = server(2.0, "4GiB", GameServerDto.GameServerStatus.RUNNING);
        GameServerEntity toStart = server(2.0, "4GiB", GameServerDto.GameServerStatus.STOPPED);

        assertThatCode(
                        () ->
                                checker.assertSufficientQuota(
                                        user, toStart, List.of(running, toStart)))
                .doesNotThrowAnyException();
    }

    @Test
    void exceedingTheQuotaIsReportedAsQuotaExceeded() {
        UserEntity user = userWithLimits(2.0, "4GiB");
        GameServerEntity running = server(2.0, "2GiB", GameServerDto.GameServerStatus.RUNNING);
        GameServerEntity toStart = server(1.0, "1GiB", GameServerDto.GameServerStatus.STOPPED);

        assertThatThrownBy(
                        () ->
                                checker.assertSufficientQuota(
                                        user, toStart, List.of(running, toStart)))
                .isInstanceOfSatisfying(
                        HardwareLimitException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(ErrorCode.HARDWARE_QUOTA_EXCEEDED));
    }

    @Test
    void serverWithoutLimitsIsReportedAsMissingLimit() {
        UserEntity user = userWithLimits(2.0, "4GiB");
        GameServerEntity toStart = new GameServerEntity();
        toStart.setStatus(GameServerDto.GameServerStatus.STOPPED);

        assertThatThrownBy(() -> checker.assertSufficientQuota(user, toStart, List.of(toStart)))
                .isInstanceOfSatisfying(
                        HardwareLimitException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(ErrorCode.SERVER_RESOURCE_LIMIT_MISSING));
    }

    private static UserEntity userWithLimits(double cpu, String memory) {
        UserEntity user = new UserEntity();
        user.setDockerHardwareLimits(limits(cpu, memory));
        return user;
    }

    private static GameServerEntity server(
            double cpu, String memory, GameServerDto.GameServerStatus status) {
        GameServerEntity server = new GameServerEntity();
        server.setDockerHardwareLimits(limits(cpu, memory));
        server.setStatus(status);
        return server;
    }

    private static DockerHardwareLimits limits(double cpu, String memory) {
        return DockerHardwareLimits.builder()
                .dockerMaxCpuCores((float) cpu)
                .dockerMemoryLimit(memory)
                .build();
    }
}
