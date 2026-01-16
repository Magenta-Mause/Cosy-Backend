package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PullProgressDto {
    private String status;
    private String id;
    private String progressDetail;
    private Long current;
    private Long total;
}
