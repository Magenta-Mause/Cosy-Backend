package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.dtos.actiondtos.FooterUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.FooterDto;
import com.magentamause.cosybackend.entities.FooterEntity;
import com.magentamause.cosybackend.security.accessmanagement.NeedsValidation;
import com.magentamause.cosybackend.security.accessmanagement.Operation;
import com.magentamause.cosybackend.services.FooterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("footer")
@Slf4j
public class FooterController {

    private final FooterService footerService;

    @GetMapping
    public ResponseEntity<FooterDto> getFooter() {
        FooterEntity footer = footerService.getFooter();
        return ResponseEntity.ok(footer.toDto());
    }

    @PutMapping
    @NeedsValidation(Operation.FOOTER_UPDATE)
    public ResponseEntity<FooterDto> updateFooter(@Valid @RequestBody FooterUpdateDto updateDto) {
        log.info("Updating footer data");
        FooterEntity updatedFooter = footerService.updateFooter(updateDto);
        return ResponseEntity.ok(updatedFooter.toDto());
    }
}
