package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.ChannelDTO;
import me.elordenador.clonetube.dtos.ChannelVideosDTO;
import me.elordenador.clonetube.dtos.FollowStateDTO;
import me.elordenador.clonetube.dtos.FollowingListDTO;
import me.elordenador.clonetube.services.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/following")
    @RequireAuth
    public FollowingListDTO listFollowing() {
        return userService.listFollowing();
    }

    @GetMapping("/{username}/channel")
    public ChannelDTO channel(@PathVariable String username) {
        return userService.channel(username);
    }

    @GetMapping("/{username}/videos")
    public ChannelVideosDTO channelVideos(
            @PathVariable String username,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer page_size) {
        return userService.channelVideos(username, page, page_size);
    }

    @GetMapping("/{username}/follow")
    public FollowStateDTO followState(@PathVariable String username) {
        return userService.followState(username);
    }

    @PostMapping("/{username}/follow")
    @RequireAuth
    public FollowStateDTO follow(@PathVariable String username) {
        return userService.follow(username);
    }

    @DeleteMapping("/{username}/follow")
    @RequireAuth
    public FollowStateDTO unfollow(@PathVariable String username) {
        return userService.unfollow(username);
    }
}
