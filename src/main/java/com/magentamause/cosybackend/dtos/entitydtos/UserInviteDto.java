package com.magentamause.cosybackend.dtos.entitydtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.entities.UserEntity;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInviteDto {
    private String uuid;
    private String username;
    private String invitedBy;
    private String inviteByUsername;
    private String secretKey;
    private Instant createdAt;
    private UserEntity.Role role;
}
