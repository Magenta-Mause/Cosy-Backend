package com.magentamause.cosybackend.security.accessmanagement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the validator for a specific operation.
 *
 * <p>The annotated method must have the signature:
 * {@code boolean methodName(ResourceResolver, Object referenceId, UserEntity user)}
 *
 * <p>Each operation must have exactly one validator. Duplicate validators for the same
 * operation will cause a startup failure.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class UserPolicy {
 *     @Validates(Operation.USER_DELETE)
 *     public boolean canDeleteUser(ResourceResolver resolver, Object referenceId, UserEntity user) {
 *         return user.getUuid().equals(referenceId);
 *     }
 * }
 * }</pre>
 *
 * @see NeedsValidation
 * @see Operation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Validates {
    Operation value();
}
