package com.ceos23.spring_boot.controller.auth.service;

import com.ceos23.spring_boot.controller.auth.dto.LoginRequest;
import com.ceos23.spring_boot.controller.auth.dto.SignupRequest;
import com.ceos23.spring_boot.controller.auth.dto.TokenResponse;
import com.ceos23.spring_boot.domain.user.entity.Role;
import com.ceos23.spring_boot.domain.user.entity.User;
import com.ceos23.spring_boot.domain.user.repository.UserRepository;
import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import com.ceos23.spring_boot.global.security.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    public static final String RT_PREFIX = "RT:";
    public static final String BLACKLIST_PREFIX = "BLACKLIST:";

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.builder()
                .email(request.email())
                .password(encodedPassword)
                .name(request.name())
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = tokenProvider.createAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        String refreshToken = tokenProvider.createRefreshToken();

        redisTemplate.opsForValue().set(
                RT_PREFIX + refreshToken,
                user.getEmail(),
                Duration.ofSeconds(tokenProvider.getRefreshTokenValiditySeconds())
        );

        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        String redisKey = RT_PREFIX + refreshToken;

        String email = redisTemplate.opsForValue().get(redisKey);

        if (email == null)
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);

        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        redisTemplate.delete(redisKey);

        String newAccessToken = tokenProvider.createAccessToken(user.getEmail(), user.getRole().name());
        String newRefreshToken = tokenProvider.createRefreshToken();

        redisTemplate.opsForValue().set(
                RT_PREFIX + newRefreshToken,
                user.getEmail(),
                Duration.ofSeconds(tokenProvider.getRefreshTokenValiditySeconds())
        );

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken, String refreshToken) {

        if (refreshToken != null)
            redisTemplate.delete(refreshToken);

        if (accessToken != null) {
            long remainingTime = tokenProvider.getRemainingExpiration(accessToken);

            if (remainingTime > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + accessToken,
                        "logout",
                        Duration.ofMillis(remainingTime)
                );
            }
        }
    }
}