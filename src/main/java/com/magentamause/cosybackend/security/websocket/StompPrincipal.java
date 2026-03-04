package com.magentamause.cosybackend.security.websocket;

import java.security.Principal;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StompPrincipal implements Principal {

    private final String userId;

    @Override
    public String getName() {
        return userId;
    }
}
