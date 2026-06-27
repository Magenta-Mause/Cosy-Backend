package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameServerFileSystemDto {

    @NotBlank private String volumeUuid;

    @Valid @Builder.Default private List<FileSystemObjectDto> objects = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class FileSystemObjectDto {

        private Integer fetchDepth;

        @NotBlank private String name;

        private FileType type;

        private Optional<Integer> permissions;
        private Optional<Integer> uid;
        private Optional<Long> size;

        @Valid
        @Builder.Default
        @ArraySchema(schema = @Schema(ref = "#/components/schemas/FileSystemObjectDto"))
        private List<FileSystemObjectDto> children = new ArrayList<>();

        public void addChild(FileSystemObjectDto child) {
            this.children.add(child);
        }
    }

    public enum FileType {
        FILE,
        DIRECTORY
    }
}
