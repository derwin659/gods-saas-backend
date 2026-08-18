package com.gods.saas.invoicing;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MifactCredentialResolver {
    private final Environment environment;
    public MifactCredentialResolver(Environment environment) { this.environment = environment; }

    public String resolveToken(String alias) {
        if (alias == null || !alias.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new IllegalArgumentException("Alias de credencial Mifact invalido");
        }
        String key = "MIFACT_TOKEN_" + alias.toUpperCase().replace('-', '_');
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("No existe la credencial segura " + key);
        }
        return value.trim();
    }
}
