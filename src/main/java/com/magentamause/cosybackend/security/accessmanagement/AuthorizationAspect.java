package com.magentamause.cosybackend.security.accessmanagement;

import com.magentamause.cosybackend.services.SecurityContextService;
import java.lang.annotation.Annotation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {
    private final SecurityContextService securityContextService;

    @Before("@annotation(requireAccess)")
    public void checkAccess(JoinPoint joinPoint, RequireAccess requireAccess) {
        Object specificId = findResourceId(joinPoint);
        securityContextService.assertUserCan(
                requireAccess.action(), requireAccess.resource(), specificId);
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
