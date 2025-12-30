package com.magentamause.cosybackend.entities;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.magentamause.cosybackend.dtos.entitydtos.UserInviteDto;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EntityListeners(AuditingEntityListener.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserInviteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    private String username;

    @Column(unique = true, nullable = false)
    private String secretKey;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "invited_by_id")
    private UserEntity invitedBy;

    @Enumerated(EnumType.STRING)
    private UserEntity.Role role;

    public UserInviteDto convertToDto() {
        return UserInviteDto.builder()
                .uuid(this.getUuid())
                .username(this.getUsername())
                .invitedBy(this.getInvitedBy() != null ? this.getInvitedBy().getUuid() : null)
                .secretKey(this.getSecretKey())
                .createdAt(this.getCreatedAt())
                .inviteByUsername(this.getInvitedBy().getUsername())
                .role(this.getRole())
                .build();
    }
}
