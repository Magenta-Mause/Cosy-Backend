package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.entities.TemplateEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Templates", description = "Game server templates")
@RequestMapping("/templates")
public interface TemplateApi {

    @Operation(summary = "Get all templates")
    @ApiResponse(responseCode = "200", description = "Templates returned")
    @GetMapping
    ResponseEntity<List<TemplateEntity>> getAllTemplates();
}
