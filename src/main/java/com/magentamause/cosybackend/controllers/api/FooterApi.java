package com.magentamause.cosybackend.controllers.api;

import com.magentamause.cosybackend.dtos.actiondtos.FooterUpdateDto;
import com.magentamause.cosybackend.dtos.entitydtos.FooterDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Footer", description = "Footer configuration")
@RequestMapping("footer")
public interface FooterApi {

    @Operation(summary = "Get footer data")
    @ApiResponse(responseCode = "200", description = "Footer returned")
    @GetMapping
    ResponseEntity<FooterDto> getFooter();

    @Operation(summary = "Update footer data")
    @ApiResponse(responseCode = "200", description = "Footer updated")
    @PutMapping
    ResponseEntity<FooterDto> updateFooter(@Valid @RequestBody FooterUpdateDto updateDto);
}
