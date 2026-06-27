package com.magentamause.cosybackend.controllers.impl;

import com.magentamause.cosybackend.controllers.api.TemplateApi;
import com.magentamause.cosybackend.entities.TemplateEntity;
import com.magentamause.cosybackend.services.core.templates.TemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TemplateController implements TemplateApi {

    private final TemplateService templateService;

    @Override
    public ResponseEntity<List<TemplateEntity>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }
}
