package com.magentamause.cosybackend.security.accessmanagement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an authorization requirement for accessing a controller or service method.
 *
 * <p>The annotation specifies the {@link Action} a caller intends to perform on a given {@link
 * Resource}. At runtime, an authorization aspect intercepts the annotated method and verifies that
 * the currently authenticated user is allowed to perform the action.
 *
 * <p>If one of the method parameters is annotated with {@link ResourceId}, its value will be used
 * as the resource reference for instance-level authorization checks (e.g. access to a specific
 * entity). If no {@code @ResourceId} is present, a resource-level check is performed.
 *
 * <p>The annotation can be placed on methods or on types (classes). When applied at type level, it
 * applies to all methods of that type unless overridden. (this is useful if you want to secure your
 * entire controller behind a auth layer)
 *
 * <p>Example:
 *
 * <pre>{@code
 * @RequireAccess(action = Action.READ, resource = Resource.USER)
 * @GetMapping("/{id}")
 * public UserDto getUser(@PathVariable @ResourceId String id) {
 *     ...
 * }
 * }</pre>
 *
 * <p>Or:
 *
 * <pre>{@code
 * @RequireAccess(action = Action.UPDATE, resource = Resource.USER)
 * @GetMapping("/{id}")
 * public UserDto getUser(@PathVariable @ResourceId GameServerEntity updatedGameServerEntity) {
 *     ...
 * }
 * }</pre>
 *
 * <p>Where this also requires:
 *
 * <pre>{@code
 * public class GameServerEntity implements Identifiable {
 *    @Override
 *    public String getId() {
 *        return this.uuid;
 *    }
 *   ...
 * }
 * }</pre>
 *
 * @see Action
 * @see Resource
 * @see ResourceId
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAccess {
    Action action();

    Resource resource();
}
