package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.FooterUpdateDto;
import com.magentamause.cosybackend.entities.FooterEntity;
import com.magentamause.cosybackend.repositories.FooterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FooterService {

    private final FooterRepository footerRepository;

    public FooterEntity getFooter() {
        return footerRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Footer not found"));
    }

    @Transactional
    public FooterEntity updateFooter(FooterUpdateDto updateDto) {
        FooterEntity footer = getFooter();
        FooterEntity updatedFooter = updateDto.applyToEntity(footer);
        return footerRepository.save(updatedFooter);
    }

    @Transactional
    public void saveFooter(FooterEntity footer) {
        footerRepository.save(footer);
    }

    public boolean isFooterAlreadyInitialized() {
        return footerRepository.count() > 0;
    }
}
