package com.justjava.mycommunity.chat.service;

import com.justjava.mycommunity.event.Event;
import com.justjava.mycommunity.chat.repository.EventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final EventRepository eventRepository;

    private static final DateTimeFormatter UTC_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String generateCalendarFromEvent(Long eventId){
        Event e = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event does not exist"));


        String uid = UUID.randomUUID() + "@mycommunity.justjava.com";
        String nowUtc = OffsetDateTime.now(ZoneOffset.UTC).format(UTC_FMT);

        ZonedDateTime startZ;
        ZonedDateTime endZ;
        String startEnd;

        if (e.getStartTime() == null) {
            startZ = e.getStartDate().atStartOfDay(ZoneOffset.UTC);
            endZ = e.getEndDate().atStartOfDay(ZoneOffset.UTC);
            startEnd = "DTSTART;VALUE=DATE:" + startZ.format(UTC_FMT) + "\r\n" +
                    "DTEND;VALUE=DATE:" + endZ.format(UTC_FMT) + "\r\n";

        }else {
            startZ = ZonedDateTime.of(e.getStartDate(), e.getStartTime(), ZoneOffset.UTC);
            endZ = ZonedDateTime.of(e.getEndDate(), e.getStartTime(), ZoneOffset.UTC);
            startEnd = "DTSTART:" + startZ.format(UTC_FMT) + "\r\n" +
                    "DTEND:" + endZ.format(UTC_FMT) + "\r\n" ;
        }

        String ics =
                "BEGIN:VCALENDAR\r\n" +
                        "PRODID:-//CONNECT//Event//EN\r\n" +
                        "VERSION:2.0\r\n" +
                        "CALSCALE:GREGORIAN\r\n" +
                        "METHOD:PUBLISH\r\n" +
                        "BEGIN:VEVENT\r\n" +
                        "UID:" + escape(uid) + "\r\n" +
                        "DTSTAMP:" + nowUtc + "\r\n" +
                        startEnd +
                        "SUMMARY:" + escape(e.getTitle()) + "\r\n" +
                        "DESCRIPTION:" + escape(e.getDescription()) + "\r\n" +
//                        "LOCATION:" + escape(location) + "\r\n" +
                        "END:VEVENT\r\n" +
                        "END:VCALENDAR\r\n";

        return ics;
       }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }
}
