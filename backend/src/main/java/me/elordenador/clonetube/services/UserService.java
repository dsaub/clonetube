package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.enums.VisibilityEnum;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.models.UserFollowsUser;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.UserFollowsUserRepository;
import me.elordenador.clonetube.repository.UserLikesVideoRepository;
import me.elordenador.clonetube.repository.UserRepository;
import me.elordenador.clonetube.repository.VideoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final UserFollowsUserRepository followsRepository;
    private final UserLikesVideoRepository likesRepository;

    public FollowingListDTO listFollowing(String username) {
        User user = requireUser(username);
        List<PublicUserDTO> users = followsRepository.findFollowedByFollowerId(user.getId()).stream()
                .map(followed -> new PublicUserDTO(followed.getId(), followed.getUsername(), followed.getFull_name()))
                .toList();
        return new FollowingListDTO(users);
    }

    public ChannelDTO channel(String username, Authentication auth) {
        User channel = findUser(username);
        User viewer = resolveOptionalUser(auth);
        boolean following = viewer != null
                && followsRepository.existsByFollowerIdAndFollowedId(viewer.getId(), channel.getId());
        long followers = followsRepository.countByFollowedId(channel.getId());
        long followingCount = followsRepository.countByFollowerId(channel.getId());
        long videoCount = viewer != null && viewer.getId().equals(channel.getId())
                ? videoRepository.countByAuthorId(channel.getId())
                : videoRepository.countByAuthorIdAndVisibility(channel.getId(), VisibilityEnum.PUBLIC);
        return new ChannelDTO(channel.getId(), channel.getUsername(), channel.getFull_name(),
                following, (int) followers, (int) followingCount, (int) videoCount);
    }

    public ChannelVideosDTO channelVideos(String username, Integer page, Integer pageSize, Authentication auth) {
        User channel = findUser(username);
        User viewer = resolveOptionalUser(auth);
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null ? 20 : Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        boolean isOwner = viewer != null && viewer.getId().equals(channel.getId());

        long total = isOwner
                ? videoRepository.countByAuthorId(channel.getId())
                : videoRepository.countByAuthorIdAndVisibility(channel.getId(), VisibilityEnum.PUBLIC);
        List<Video> videos = isOwner
                ? videoRepository.findAllByAuthorIdOrderByCreatedAtDescIdDesc(channel.getId(), PageRequest.of(p - 1, ps))
                : videoRepository.findAllByAuthorIdAndVisibilityOrderByCreatedAtDescIdDesc(
                        channel.getId(), VisibilityEnum.PUBLIC, PageRequest.of(p - 1, ps));

        Map<Integer, Long> likes = countLikes(videos.stream().map(Video::getId).toList());
        List<ChannelVideoItemDTO> items = videos.stream().map(video -> new ChannelVideoItemDTO(
                video.getId(), video.getFilename(), video.getVideo_name(), video.getVideo_desc(),
                video.getVisibility().toApi(), video.getCreated_at().toInstant().toString(),
                likes.getOrDefault(video.getId(), 0L).intValue())).toList();

        return new ChannelVideosDTO(items, p, ps, (int) total, pages((int) total, ps));
    }

    public FollowStateDTO followState(String username, Authentication auth) {
        User target = findUser(username);
        User viewer = resolveOptionalUser(auth);
        boolean following = viewer != null
                && followsRepository.existsByFollowerIdAndFollowedId(viewer.getId(), target.getId());
        return new FollowStateDTO(target.getUsername(), following, (int) followsRepository.countByFollowedId(target.getId()));
    }

    @Transactional
    public FollowStateDTO follow(String username, String targetUsername) {
        User me = requireUser(username);
        User target = findUser(targetUsername);
        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes seguirte a ti mismo");
        }
        if (!followsRepository.existsByFollowerIdAndFollowedId(me.getId(), target.getId())) {
            UserFollowsUser link = new UserFollowsUser();
            link.setFollower(me);
            link.setFollowed(target);
            link.setCreated_at(Date.from(Instant.now()));
            followsRepository.save(link);
        }
        return state(target, me);
    }

    @Transactional
    public FollowStateDTO unfollow(String username, String targetUsername) {
        User me = requireUser(username);
        User target = findUser(targetUsername);
        followsRepository.findByFollowerIdAndFollowedId(me.getId(), target.getId())
                .ifPresent(followsRepository::delete);
        return state(target, me);
    }

    private FollowStateDTO state(User target, User viewer) {
        return new FollowStateDTO(target.getUsername(),
                followsRepository.existsByFollowerIdAndFollowedId(viewer.getId(), target.getId()),
                (int) followsRepository.countByFollowedId(target.getId()));
    }

    private Map<Integer, Long> countLikes(List<Integer> videoIds) {
        Map<Integer, Long> likes = new HashMap<>();
        if (videoIds.isEmpty()) return likes;
        for (Object[] row : likesRepository.countByVideoIds(videoIds)) {
            likes.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return likes;
    }

    private int pages(int total, int pageSize) {
        return Math.max(1, (int) Math.ceil((double) total / pageSize));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authed user not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private User resolveOptionalUser(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return userRepository.findByUsername(auth.getName()).orElse(null);
        }
        return null;
    }
}
