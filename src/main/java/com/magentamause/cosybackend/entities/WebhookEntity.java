package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookType webhookType;

    @Column(nullable = false)
    private String webhookUrl;

    @Builder.Default private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "game_server_webhook_events",
            joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<GameServerEventType> subscribedEvents = new HashSet<>();

    public WebhookDto toDto() {
        return WebhookDto.builder()
                .uuid(uuid)
                .webhookType(webhookType)
                .webhookUrl(webhookUrl)
                .enabled(enabled)
                .subscribedEvents(subscribedEvents)
                .build();
    }
}
