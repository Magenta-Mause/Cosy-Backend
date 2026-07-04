package com.magentamause.cosybackend.controllers.impl;

import com.magentamause.cosybackend.controllers.api.FooterApi;
import com.magentamause.cosybackend.dtos.actiondtos.FooterUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.FooterDto;
import com.magentamause.cosybackend.entities.FooterEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.services.FooterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FooterController implements FooterApi {

    private final FooterService footerService;

    @Override
    public ResponseEntity<FooterDto> getFooter() {
        FooterEntity footer = footerService.getFooter();
        return ResponseEntity.ok(footer.toDto());
    }

    @Override
    @NeedsValidation(Operation.FOOTER_UPDATE)
    public ResponseEntity<FooterDto> updateFooter(FooterUpdateDto updateDto) {
        log.info("Updating footer data");
        FooterEntity updatedFooter = footerService.updateFooter(updateDto);
        return ResponseEntity.ok(updatedFooter.toDto());
    }
}
