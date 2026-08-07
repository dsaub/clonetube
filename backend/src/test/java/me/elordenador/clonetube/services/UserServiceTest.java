package me.elordenador.clonetube.services;

import me.elordenador.clonetube.dtos.ChannelDTO;
import me.elordenador.clonetube.dtos.ChannelVideosDTO;
import me.elordenador.clonetube.dtos.FollowStateDTO;
import me.elordenador.clonetube.enums.VisibilityEnum;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.models.UserFollowsUser;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.UserFollowsUserRepository;
import me.elordenador.clonetube.repository.UserLikesVideoRepository;
import me.elordenador.clonetube.repository.UserRepository;
import me.elordenador.clonetube.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock VideoRepository videoRepository;
    @Mock UserFollowsUserRepository followsRepository;
    @Mock UserLikesVideoRepository likesRepository;

    UserService service;

    private final User me = User.builder().id(1).username("alice").full_name("Alice").password_version(0).build();
    private final User target = User.builder().id(2).username("bob").full_name("Bob").password_version(0).build();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, videoRepository, followsRepository, likesRepository);
    }

    private Video video(VisibilityEnum visibility) {
        Video v = new Video();
        v.setId(1);
        v.setFilename("videos/1.mp4");
        v.setAuthor(target);
        v.setVideo_name("t");
        v.setVideo_desc("");
        v.setCreated_at(new Date());
        v.setVisibility(visibility);
        return v;
    }

    @Test
    void follow_is_idempotent_and_blocks_self_follow() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(me));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(target));
        when(followsRepository.existsByFollowerIdAndFollowedId(1, 2)).thenReturn(false, true);
        when(followsRepository.countByFollowedId(2)).thenReturn(1L);

        FollowStateDTO state = service.follow("alice", "bob");

        assertTrue(state.getFollowing());
        verify(followsRepository).save(any(UserFollowsUser.class));
        verify(followsRepository, never()).delete(any());

        when(followsRepository.existsByFollowerIdAndFollowedId(1, 2)).thenReturn(true);
        service.follow("alice", "bob");
        verify(followsRepository, times(1)).save(any(UserFollowsUser.class));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.follow("alice", "alice"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void unfollow_is_idempotent() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(me));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(target));
        when(followsRepository.findByFollowerIdAndFollowedId(1, 2))
                .thenReturn(Optional.of(new UserFollowsUser(me, target, new Date())));
        when(followsRepository.countByFollowedId(2)).thenReturn(0L);

        FollowStateDTO state = service.unfollow("alice", "bob");
        assertFalse(state.getFollowing());
        verify(followsRepository).delete(any(UserFollowsUser.class));

        when(followsRepository.findByFollowerIdAndFollowedId(1, 2)).thenReturn(Optional.empty());
        service.unfollow("alice", "bob");
        verify(followsRepository, times(1)).delete(any(UserFollowsUser.class));
    }

    @Test
    void channel_owner_sees_everything_others_only_public() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(target));
        when(followsRepository.existsByFollowerIdAndFollowedId(anyInt(), anyInt())).thenReturn(false);
        when(followsRepository.countByFollowedId(2)).thenReturn(5L);
        when(followsRepository.countByFollowerId(2)).thenReturn(3L);

        when(videoRepository.countByAuthorId(2)).thenReturn(7L);
        ChannelDTO own = service.channel("bob", authOf(target));
        assertEquals(7, own.getVideo_count());

        when(videoRepository.countByAuthorIdAndVisibility(2, VisibilityEnum.PUBLIC)).thenReturn(2L);
        ChannelDTO visitor = service.channel("bob", null);
        assertEquals(2, visitor.getVideo_count());
        assertEquals(5, visitor.getFollowers());
    }

    @Test
    void channel_videos_are_paginated_and_sorted() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(target));
        when(videoRepository.countByAuthorIdAndVisibility(2, VisibilityEnum.PUBLIC)).thenReturn(21L);
        when(videoRepository.findAllByAuthorIdAndVisibilityOrderByCreatedAtDescIdDesc(
                eq(2), eq(VisibilityEnum.PUBLIC), any())).thenReturn(List.of(video(VisibilityEnum.PUBLIC)));
        when(likesRepository.countByVideoIds(List.of(1))).thenReturn(List.<Object[]>of(new Object[]{1, 3L}));

        ChannelVideosDTO page = service.channelVideos("bob", 2, 20, null);

        assertEquals(21, page.getTotal());
        assertEquals(2, page.getPages());
        assertEquals(1, page.getVideos().size());
        assertEquals("public", page.getVideos().get(0).getVisibility());
        assertEquals(3, page.getVideos().get(0).getLikes());
    }

    private org.springframework.security.core.Authentication authOf(User user) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of());
    }
}
