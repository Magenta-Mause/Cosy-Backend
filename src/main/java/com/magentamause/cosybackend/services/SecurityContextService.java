package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.exceptions.NoAuthenticationFoundException;
import com.magentamause.cosybackend.security.accessmanagement.Action;
import com.magentamause.cosybackend.security.accessmanagement.Resource;
import com.magentamause.cosybackend.security.accessmanagement.policies.AccessPolicy;
import com.magentamause.cosybackend.security.jwtfilter.AuthenticationToken;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class SecurityContextService {

    private final Map<Resource, AccessPolicy> policies;

    public SecurityContextService(List<AccessPolicy> policies) {
        this.policies =
                policies.stream()
                        .collect(Collectors.toMap(AccessPolicy::resource, Function.identity()));
    }

    public AuthenticationToken getAuthenticationToken() {
        Object auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof AuthenticationToken)) {
            throw new NoAuthenticationFoundException();
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
        return getAuthenticationToken().getUser();
    }

    public void assertUserHasAccessOfRole(UserEntity.Role role) {
        if (getUser().getRole().equals(UserEntity.Role.OWNER)) {
            return;
        }

        if (!getAuthenticationToken().getUser().getRole().equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    public void assertUserCan(Action action, Resource resource, Object referenceId) {
        AccessPolicy policy = policies.get(resource);
        if (policy == null) {
            throw new IllegalStateException("No policy for resource " + resource);
        }

        log.debug("Checking if user {} can {} {}", getUser().getUsername(), action, referenceId);
        boolean allowed = policy.can(getUser(), action, referenceId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }
}
