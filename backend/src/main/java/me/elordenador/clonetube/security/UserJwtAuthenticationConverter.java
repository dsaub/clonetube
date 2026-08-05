package me.elordenador.clonetube.security;

import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    private final UserRepository userRepository;

    public UserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt source) {
        String username = source.getSubject();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Unknown user"))); // ponytail: JWT came from us, but user was deleted; treat as invalid token

        Object pwdVerClaim = source.getClaim("pwd_ver");
        Integer tokenPasswordVersion = pwdVerClaim instanceof Number n ? n.intValue() : null;
        if (tokenPasswordVersion == null || !tokenPasswordVersion.equals(user.getPassword_version())) {
            throw new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Password changed; token invalidated"));
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (Boolean.TRUE.equals(user.getIs_admin())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        return new JwtAuthenticationToken(source, authorities, username);
    }
}
