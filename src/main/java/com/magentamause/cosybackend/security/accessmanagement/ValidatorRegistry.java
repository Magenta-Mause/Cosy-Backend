package com.magentamause.cosybackend.security.accessmanagement;

import com.magentamause.cosybackend.entities.UserEntity;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Registry that maps operations to their validator methods.
 * Scans all Spring beans on first access for methods annotated with {@link Validates}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidatorRegistry {

    private final ApplicationContext applicationContext;
    private Map<Operation, ValidatorEntry> validators;

    private synchronized void ensureInitialized() {
        if (validators != null) {
            return;
        }
        validators = new EnumMap<>(Operation.class);
        scanValidators();
    }

    private void scanValidators() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            for (Method method : beanClass.getDeclaredMethods()) {
                Validates validates = method.getAnnotation(Validates.class);
                if (validates != null) {
                    registerValidator(validates.value(), method, bean);
                }
            }
        }

        log.info("Registered {} authorization validators", validators.size());
    }

    private void registerValidator(Operation operation, Method method, Object bean) {
        if (validators.containsKey(operation)) {
            throw new IllegalStateException(
                    "Duplicate validator for operation: " + operation
                            + ". Found in both " + validators.get(operation).method().getName()
                            + " and " + method.getName());
        }

        validateMethodSignature(method);
        method.setAccessible(true);

        validators.put(operation, new ValidatorEntry(bean, method));
        log.debug("Registered validator for {}: {}.{}", operation, bean.getClass().getSimpleName(), method.getName());
    }

    private void validateMethodSignature(Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 3
                || !ResourceResolver.class.isAssignableFrom(params[0])
                || !Object.class.isAssignableFrom(params[1])
                || !UserEntity.class.isAssignableFrom(params[2])) {
            throw new IllegalStateException(
                    "Validator method " + method.getName()
                            + " must have signature (ResourceResolver, Object, UserEntity) -> boolean");
        }
        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            throw new IllegalStateException(
                    "Validator method " + method.getName() + " must return boolean");
        }
    }

    public ValidatorEntry getValidator(Operation operation) {
        ensureInitialized();
        ValidatorEntry entry = validators.get(operation);
        if (entry == null) {
            throw new IllegalStateException("No validator registered for operation: " + operation);
        }
        return entry;
    }

    public record ValidatorEntry(Object bean, Method method) {
        public boolean invoke(ResourceResolver resolver, Object referenceId, UserEntity user) {
            try {
                return (boolean) method.invoke(bean, resolver, referenceId, user);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke validator: " + method.getName(), e);
            }
        }
    }
}
