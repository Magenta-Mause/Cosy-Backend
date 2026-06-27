package com.magentamause.cosybackend.controllers.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Root")
@RequestMapping
public interface RootApi {

    @Operation(summary = "Health check")
    @GetMapping
    ResponseEntity<String> root();
}
