package org.keinus.logparser.interfaces.exception;

import java.util.List;

public class ConfigValidationException extends RuntimeException {
    private final List<String> validationErrors;

    public ConfigValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    public ConfigValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
