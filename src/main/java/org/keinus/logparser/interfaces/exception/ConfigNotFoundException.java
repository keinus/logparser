package org.keinus.logparser.interfaces.exception;

public class ConfigNotFoundException extends RuntimeException {
    private final String entityType;
    private final Object entityId;

    public ConfigNotFoundException(String entityType, Object entityId) {
        super(String.format("%s with id '%s' not found", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public ConfigNotFoundException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
    }

    public String getEntityType() {
        return entityType;
    }

    public Object getEntityId() {
        return entityId;
    }
}
