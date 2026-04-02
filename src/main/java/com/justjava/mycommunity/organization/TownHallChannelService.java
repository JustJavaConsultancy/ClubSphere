package com.justjava.mycommunity.organization;

import com.justjava.mycommunity.chat.repository.ChannelRepository;
import com.justjava.mycommunity.chat.repository.TownHallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TownHallChannelService {

    private final ChannelRepository channelRepository;
    private final TownHallRepository townHallRepository;

    public Channel createChannel(String name, String description) {
        Channel channel = new Channel();
        channel.setName(name);
        channel.setDescription(description);
        return channelRepository.save(channel);
    }

    public TownHall createTownHall(String name, String description){
        TownHall townHall = new TownHall();
        townHall.setName(name);
        townHall.setDescription(description);
        return townHallRepository.save(townHall);
    }
}
