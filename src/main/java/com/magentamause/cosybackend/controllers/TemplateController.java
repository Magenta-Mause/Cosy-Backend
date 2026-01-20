package com.magentamause.cosybackend.controllers;

import com.magentamause.cosybackend.entities.TemplateEntity;
import com.magentamause.cosybackend.services.core.templates.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/templates")
    public ResponseEntity<List<TemplateEntity>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }
}
