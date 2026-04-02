package com.justjava.mycommunity.util;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component("stringUtils")  // The name here is important!
public class StringUtils {

    public static String InstantToStringDate(Instant instant) {
        ZonedDateTime dateTime = instant.atZone(ZoneId.systemDefault());
        LocalDate messageDate = dateTime.toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");
        if (messageDate.equals(today)) {
            return "Today, " + dateTime.format(timeFormatter);
        } else if (messageDate.equals(today.minusDays(1))) {
            return "Yesterday, " + dateTime.format(timeFormatter);
        } else {
            return dateTime.format(fullFormatter);
        }
    }

    public static String offsetDateTimeToStringDate(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return "";
        }

        // Convert to system default zone for display
        ZonedDateTime dateTime = offsetDateTime.atZoneSameInstant(ZoneId.systemDefault());
        LocalDate messageDate = dateTime.toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");

        if (messageDate.equals(today)) {
            return "Today, " + dateTime.format(timeFormatter);
        } else if (messageDate.equals(today.minusDays(1))) {
            return "Yesterday, " + dateTime.format(timeFormatter);
        } else {
            return dateTime.format(fullFormatter);
        }
    }

}
