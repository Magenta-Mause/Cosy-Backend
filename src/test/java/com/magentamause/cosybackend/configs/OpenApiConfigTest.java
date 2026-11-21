package com.magentamause.cosybackend.configs;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    /** OpenAPI 3.x: keys of the components object must match this pattern. */
    private static final String COMPONENT_KEY_PATTERN = "^[a-zA-Z0-9.\\-_]+$";

    private final OpenAPI openApi = new OpenApiConfig().openAPI();

    @Test
    void securitySchemeKeysAreValidComponentKeys() {
        // The frontend's client generator (orval 8) refuses a spec with an invalid key.
        assertThat(openApi.getComponents().getSecuritySchemes().keySet())
                .isNotEmpty()
                .allMatch(key -> key.matches(COMPONENT_KEY_PATTERN));
    }

    @Test
    void globalSecurityRequirementReferencesTheDeclaredScheme() {
        assertThat(openApi.getSecurity())
                .singleElement()
                .satisfies(
                        requirement ->
                                assertThat(requirement.keySet())
                                        .containsExactly(OpenApiConfig.SECURITY_SCHEME_NAME));
        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
    }
}
