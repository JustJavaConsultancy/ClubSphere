package com.justjava.mycommunity.event;

import com.justjava.mycommunity.account.AuthenticationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class ParticipantController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private EventService eventService;

    public String myEvents(){
        List<Event> events = eventService.getMyEvents((String) authenticationManager.get("sub"));
        return "participant/myEvents";
    }
}
