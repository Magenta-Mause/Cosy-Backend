package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.actiondtos.UserInviteCreationDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.UserInviteEntity;
import com.magentamause.cosybackend.repositories.UserInviteRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserInviteService {
    private static final char[] POSSIBLE_CHARACTERS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int KEY_LENGTH = 16;
    private final UserInviteRepository userInviteRepository;
    private final UserEntityService userEntityService;
    private final PasswordEncoder passwordEncoder;

    public List<UserInviteEntity> getAllInvites() {
        return userInviteRepository.findAll();
    }

    public void revokeInvite(String inviteUuid) {
        getInviteByUuidOrElseThrow(inviteUuid);
        userInviteRepository.deleteById(inviteUuid);
    }

    public UserInviteEntity createInvite(
            String ownerCreationId, UserInviteCreationDto userInviteCreationDto) {
        if (!Objects.isNull(userInviteCreationDto.getUsername())) {
            if (userInviteRepository.existsByUsername(userInviteCreationDto.getUsername())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Invite with the given username already exists");
            }
            if (userEntityService.existsByUsername(userInviteCreationDto.getUsername())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A user with the given username already exists");
            }
        }

        UserInviteEntity invite =
                UserInviteEntity.builder()
                        .invitedBy(userEntityService.getUserByUuid(ownerCreationId))
                        .secretKey(generateRandomKey())
                        .username(userInviteCreationDto.getUsername())
                        .role(userInviteCreationDto.getRole())
                        .build();

        return userInviteRepository.save(invite);
    }

    public UserInviteEntity getInviteBySecretKeyOrElseThrow(String secretToken) {
        return userInviteRepository
                .findBySecretKey(secretToken)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Invite not found"));
    }

    public UserInviteEntity getInviteByUuidOrElseThrow(String inviteUuid) {
        return userInviteRepository
                .findById(inviteUuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Invite not found"));
    }

    private UserInviteEntity getInviteBySecretKeyWithLockOrElseThrow(String username) {
        return userInviteRepository
                .findBySecretKeyLocked(username)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Invite not found"));
    }

    @Transactional
    public UserEntity useInvite(String secretKey, String username, String password) {
        UserInviteEntity invite = getInviteBySecretKeyWithLockOrElseThrow(secretKey);

        UserEntity.Role inviteRole =
                switch (invite.getRole()) {
                    case null -> UserEntity.Role.QUOTA_USER;
                    case QUOTA_USER -> UserEntity.Role.QUOTA_USER;
                    case ADMIN, OWNER -> UserEntity.Role.ADMIN;
                };

        UserEntity.UserEntityBuilder userBuilder =
                UserEntity.builder()
                        .role(inviteRole)
                        .password(passwordEncoder.encode(password))
                        .defaultPasswordReset(true);

        if (Objects.isNull(invite.getUsername())) {
            userBuilder.username(username);
        } else {
            userBuilder.username(invite.getUsername());
        }

        UserEntity builtUser = userBuilder.build();
        if (userEntityService.existsByUsername(builtUser.getUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A user with the given username already exists");
        }
        UserEntity user = userEntityService.saveUserEntity(builtUser);
        userInviteRepository.delete(invite);
        log.info("Invite [{}] used for user {}", invite.getUuid(), user.getUsername());
        return user;
    }

    private String generateRandomKey() {
        SecureRandom random = new SecureRandom();
        return random.ints(0, POSSIBLE_CHARACTERS.length)
                .limit(KEY_LENGTH)
                .map(i -> POSSIBLE_CHARACTERS[i])
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
