package com.magentamause.cosybackend.entities;

import com.magentamause.cosybackend.dtos.entitydtos.WebhookDto;
import jakarta.persistence.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    /**
     * Request method for {@link WebhookType#CUSTOM} webhooks. Null for the built-in integrations,
     * which always POST.
     */
    @Enumerated(EnumType.STRING)
    private WebhookHttpMethod httpMethod;

    /**
     * Request body for {@link WebhookType#CUSTOM} webhooks, with {@code {{placeholder}}} tokens
     * resolved at send time. Null or blank means "send no body".
     */
    @Column(columnDefinition = "text")
    private String bodyTemplate;

    /**
     * Request headers for {@link WebhookType#CUSTOM} webhooks. Values may contain placeholders too;
     * header names never do.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "game_server_webhook_headers",
            joinColumns = @JoinColumn(name = "webhook_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value", length = 2048, nullable = false)
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    public WebhookDto toDto() {
        return WebhookDto.builder()
                .uuid(uuid)
                .webhookType(webhookType)
                .webhookUrl(webhookUrl)
                .enabled(enabled)
                .subscribedEvents(subscribedEvents)
                .httpMethod(httpMethod)
                .bodyTemplate(bodyTemplate)
                .headers(headers == null ? new HashMap<>() : new HashMap<>(headers))
                .build();
    }
}
