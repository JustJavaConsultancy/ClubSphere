package com.justjava.mycommunity.util;

import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component("dateTimeUtils")
public class DateTimeUtils {
    public String subtractDays(String dueDate, int daysBefore) {
        dueDate = dueDate + "T00:00:00Z";
        ZonedDateTime parsedDate = ZonedDateTime.parse(dueDate, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        ZonedDateTime reminderDate = parsedDate.minusDays(daysBefore);
//        System.out.println("This is the reminder date in ISO FORMAT " +
//                reminderDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
        return reminderDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
}
