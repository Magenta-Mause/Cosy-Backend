package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.configs.properties.FooterProperties;
import com.magentamause.cosybackend.entities.FooterEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(FooterProperties.class)
public class FooterInitializationService {

    private final FooterProperties footerProperties;
    private final FooterService footerService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeFooter() {
        log.info("Initializing footer data...");

        if (footerService.isFooterAlreadyInitialized()) {
            log.info("Footer data already exists");
            return;
        }

        FooterEntity footer =
                FooterEntity.builder()
                        .fullName(footerProperties.fullName())
                        .email(footerProperties.email())
                        .phone(footerProperties.phone())
                        .street(footerProperties.street())
                        .city(footerProperties.city())
                        .build();

        footerService.saveFooter(footer);
        log.info("Footer data initialized successfully");
    }
}
