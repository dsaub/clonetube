package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.ChannelDTO;
import me.elordenador.clonetube.dtos.ChannelVideosDTO;
import me.elordenador.clonetube.dtos.FollowStateDTO;
import me.elordenador.clonetube.dtos.FollowingListDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    public FollowingListDTO listFollowing() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public ChannelDTO channel(String username) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public ChannelVideosDTO channelVideos(String username, Integer page, Integer page_size) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public FollowStateDTO followState(String username) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public FollowStateDTO follow(String username) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public FollowStateDTO unfollow(String username) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
