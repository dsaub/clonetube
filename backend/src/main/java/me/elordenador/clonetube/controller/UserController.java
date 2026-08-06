package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.ChannelDTO;
import me.elordenador.clonetube.dtos.ChannelVideosDTO;
import me.elordenador.clonetube.dtos.FollowStateDTO;
import me.elordenador.clonetube.dtos.FollowingListDTO;
import me.elordenador.clonetube.services.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/following")
    @RequireAuth
    public FollowingListDTO listFollowing(Authentication auth) {
        return userService.listFollowing(auth.getName());
    }

    @GetMapping("/{username}/channel")
    public ChannelDTO channel(Authentication auth, @PathVariable String username) {
        return userService.channel(username, auth);
    }

    @GetMapping("/{username}/videos")
    public ChannelVideosDTO channelVideos(Authentication auth,
                                          @PathVariable String username,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer page_size) {
        return userService.channelVideos(username, page, page_size, auth);
    }

    @GetMapping("/{username}/follow")
    public FollowStateDTO followState(Authentication auth, @PathVariable String username) {
        return userService.followState(username, auth);
    }

    @PostMapping("/{username}/follow")
    @RequireAuth
    public FollowStateDTO follow(Authentication auth, @PathVariable String username) {
        return userService.follow(auth.getName(), username);
    }

    @DeleteMapping("/{username}/follow")
    @RequireAuth
    public FollowStateDTO unfollow(Authentication auth, @PathVariable String username) {
        return userService.unfollow(auth.getName(), username);
    }
}
