package com.magentamause.cosybackend.controllers.impl;

import com.magentamause.cosybackend.controllers.api.RootApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController implements RootApi {

    @Override
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("Hello World!");
    }
}
