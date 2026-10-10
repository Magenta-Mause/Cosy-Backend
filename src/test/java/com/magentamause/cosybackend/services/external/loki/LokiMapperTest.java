package com.magentamause.cosybackend.services.external.loki;

import static org.assertj.core.api.Assertions.assertThat;

import com.magentamause.cosybackend.dtos.loki.LokiData;
import com.magentamause.cosybackend.dtos.loki.LokiQueryResponse;
import com.magentamause.cosybackend.dtos.loki.LokiStreamResult;
import com.magentamause.cosybackend.entities.loki.GameServerLogMessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LokiMapperTest {

    private static final String SERVER = "49737e05-199e-4848-ab51-1d73249f7dcf";

    @Test
    void mergesStreamsIntoOneChronologicalList() {
        // Loki orders entries per stream (here: newest first), one stream per log level.
        LokiQueryResponse response =
                response(
                        stream(
                                "INFO",
                                List.of("3000000005", "info newest"),
                                List.of("1000000001", "info oldest")),
                        stream("ERROR", List.of("2000000003", "error middle")));

        List<GameServerLogMessageEntity> logs = LokiMapper.toEntities(response);

        assertThat(logs)
                .extracting(GameServerLogMessageEntity::getMessage)
                .containsExactly("info oldest", "error middle", "info newest");
    }

    @Test
    void keepsNanosecondPrecision() {
        LokiQueryResponse response =
                response(stream("INFO", List.of("1790510400123456789", "precise line")));

        List<GameServerLogMessageEntity> logs = LokiMapper.toEntities(response);

        assertThat(logs.getFirst().getTimestamp())
                .isEqualTo(Instant.ofEpochSecond(1_790_510_400L, 123_456_789L));
    }

    @Test
    void emptyResponseYieldsNoLogs() {
        assertThat(LokiMapper.toEntities(null)).isEmpty();
    }

    @SafeVarargs
    private static LokiStreamResult stream(String level, List<String>... values) {
        return new LokiStreamResult(Map.of("level", level, "server_uuid", SERVER), List.of(values));
    }

    private static LokiQueryResponse response(LokiStreamResult... streams) {
        return new LokiQueryResponse("success", new LokiData("streams", List.of(streams), null));
    }
}
