package com.justjava.mycommunity.chat;

import com.justjava.mycommunity.chat.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/event/{id}")
    public ResponseEntity<byte[]> downloadIcs(@PathVariable(name = "id") Long eventId) {

        try {
            String o = calendarService.generateCalendarFromEvent(eventId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("event-" + eventId + ".ics").build());
            return new ResponseEntity<>(o.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
        } catch (Exception e) {
            return  new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

