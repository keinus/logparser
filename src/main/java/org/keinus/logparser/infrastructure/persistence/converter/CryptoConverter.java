package org.keinus.logparser.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Converter
@Slf4j
public class CryptoConverter implements AttributeConverter<String, String> {

    // TODO: Move these to configuration
    private static final String SECRET_KEY = "your-secret-key-change-this-in-production";
    private static final String SALT = "deadbeef";

    private final TextEncryptor encryptor = Encryptors.text(SECRET_KEY, SALT);

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.trim().isEmpty()) {
            return attribute;
        }
        try {
            return encryptor.encrypt(attribute);
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return dbData;
        }
        try {
            return encryptor.decrypt(dbData);
        } catch (Exception e) {
            log.warn("Error decrypting data, returning original value", e);
            return dbData;
        }
    }
}
