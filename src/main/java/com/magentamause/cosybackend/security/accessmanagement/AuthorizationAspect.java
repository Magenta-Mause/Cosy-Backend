package com.magentamause.cosybackend.security.accessmanagement;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.services.auth.SecurityContextService;

import java.lang.annotation.Annotation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {
    private final SecurityContextService securityContextService;
    private final ValidatorRegistry validatorRegistry;
    private final ResourceResolver resourceResolver;

    @Before("@annotation(needsValidation)")
    public void checkAccess(JoinPoint joinPoint, NeedsValidation needsValidation) {
        UserEntity user = securityContextService.getUser();

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        if (user.getRole().isAdmin()) {
            return;
        }

        Object resourceId = findResourceId(joinPoint);

        ValidatorRegistry.ValidatorEntry validator =
                validatorRegistry.getValidator(needsValidation.value());

        boolean allowed = validator.invoke(resourceResolver, resourceId, user);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    private Object findResourceId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof ResourceId) {
                    Object resourceId = args[i];
                    if (resourceId instanceof Identifiable) {
                        return ((Identifiable) resourceId).getId();
                    }
                    return args[i];
                }
            }
        }
        return null;
    }
}
