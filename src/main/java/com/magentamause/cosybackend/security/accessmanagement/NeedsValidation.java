package com.magentamause.cosybackend.security.accessmanagement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method requires authorization validation for the specified operation.
 *
 * <p>At runtime, the {@link AuthorizationAspect} intercepts the annotated method, looks up the
 * validator registered for the operation via {@link Validates}, and invokes it with the current
 * user and resource ID.
 *
 * <p>If one of the method parameters is annotated with {@link ResourceId}, its value will be used
 * as the resource reference for instance-level authorization checks.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @NeedsValidation(Operation.USER_DELETE)
 * @DeleteMapping("/{id}")
 * public void deleteUser(@PathVariable @ResourceId String id) {
 *     ...
 * }
 * }</pre>
 *
 * @see Validates
 * @see ResourceId
 * @see Operation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NeedsValidation {
    Operation value();

    boolean allowUnauthorized() default false;
}
