package dev.shipping.shipments.utils;


import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MyResourceUtils {

    // Target timezone constant
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    
    // Pattern formatter constant (Thread-safe and reused for performance)
    private static final DateTimeFormatter IST_FORMATTER = 
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a z", Locale.ENGLISH);

    // Private constructor prevents instantiation of this utility class
    private MyResourceUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Converts a ZonedDateTime to Indian Standard Time (IST) and formats it.
     * @param passedDateTime The source date time object
     * @return Formatted string like "28-MAY-2026 07:37:42 AM IST" or null
     */
    public static String getFormattedDateTime(ZonedDateTime passedDateTime) {
        if (passedDateTime == null) {
            return null;
        }
        
        ZonedDateTime istDateTime = passedDateTime.withZoneSameInstant(IST_ZONE);
        return istDateTime.format(IST_FORMATTER).toUpperCase();
    }
}

