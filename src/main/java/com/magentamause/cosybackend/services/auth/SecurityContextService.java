package com.magentamause.cosybackend.services.auth;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.security.jwtfilter.AuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SecurityContextService {

    public AuthenticationToken getAuthenticationToken() {
        Object auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof AuthenticationToken)) {
            return null;
        }
        return (AuthenticationToken) auth;
    }

    public String getUsername() {
        return getAuthenticationToken().getUser().getUsername();
    }

    public String getUserId() {
        return getAuthenticationToken().getUserId();
    }

    public UserEntity getUser() {
        AuthenticationToken token = getAuthenticationToken();
        if (token == null) {
            return null;
        }
        return token.getUser();
    }
}
