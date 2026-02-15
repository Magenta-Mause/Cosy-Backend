package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.services.core.gameserver.webhookSender.GameServerDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookService webhookService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameServerDomainEvent(GameServerDomainEvent event) {
        webhookService.dispatch(event);
    }
}
