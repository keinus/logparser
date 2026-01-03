package org.keinus.logparser.infrastructure.util.converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimestampParser {
    private static final Logger log = LoggerFactory.getLogger(TimestampParser.class);
    
    private static final List<DateTimeFormatter> FORMATTERS = new ArrayList<>();

    static {
        // ISO-8601 variations
        FORMATTERS.add(DateTimeFormatter.ISO_INSTANT);
        FORMATTERS.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        FORMATTERS.add(DateTimeFormatter.ISO_DATE_TIME);
        
        // Common Log Formats
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH));
        FORMATTERS.add(DateTimeFormatter.ofPattern("MMM dd HH:mm:ss", Locale.ENGLISH)); // Syslog (Legacy)
        FORMATTERS.add(DateTimeFormatter.ofPattern("MMM dd HH:mm:ss yyyy", Locale.ENGLISH));
        FORMATTERS.add(DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH)); // Apache Common
    }

    /**
     * Parses various date string formats into Instant.
     * Returns current time if parsing fails (safe fallback) or throws Exception depending on strict mode.
     * Here we return null on failure to let caller decide.
     */
    public static Instant parse(Object dateObj) {
        if (dateObj == null) {
            return null;
        }

        if (dateObj instanceof Instant) {
            return (Instant) dateObj;
        }
        
        if (dateObj instanceof Long) {
            return Instant.ofEpochMilli((Long) dateObj);
        }

        String dateStr = dateObj.toString().trim();
        
        // Try Numeric Epoch
        try {
            long epoch = Long.parseLong(dateStr);
            // Heuristic: If > 10^11, likely millis, else seconds
            if (epoch > 100000000000L) { 
                return Instant.ofEpochMilli(epoch);
            } else {
                return Instant.ofEpochSecond(epoch);
            }
        } catch (NumberFormatException ignored) {
            // Not a number, proceed to patterns
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                // Try parsing as Zoned/Offset first
                return Instant.from(formatter.parse(dateStr));
            } catch (DateTimeParseException ignored) {
                try {
                    // Try parsing as Local and assume system default zone
                    LocalDateTime localDt = LocalDateTime.parse(dateStr, formatter);
                    return localDt.atZone(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignored2) {
                    // Continue
                }
            } catch (Exception e) {
                // Continue
            }
        }
        
        log.debug("Failed to parse timestamp: {}", dateStr);
        return null;
    }
}
