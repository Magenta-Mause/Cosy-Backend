package com.magentamause.cosybackend.services.user;

import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.entities.UserEntity.Role;
import com.magentamause.cosybackend.entities.gameserver.utility.DockerHardwareLimits;
import com.magentamause.cosybackend.repositories.UserEntityRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEntityService {

    private final UserEntityRepository userEntityRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserEntity> getAllUsers() {
        return userEntityRepository.findAll();
    }

    public UserEntity getUserByUuid(String uuid) {
        return userEntityRepository
                .findById(uuid)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User with uuid " + uuid + " not found"));
    }

    public Optional<UserEntity> getOptionalUserByUuid(String uuid) {
        return userEntityRepository.findById(uuid);
    }

    public UserEntity getUserByUsername(String username) {
        return userEntityRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User with username " + username + " not found"));
    }

    public UserEntity saveUserEntity(UserEntity userEntity) {
        return userEntityRepository.save(userEntity);
    }

    public void deleteUserByUuid(String uuid) {
        UserEntity user =
                userEntityRepository
                        .findById(uuid)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "User with uuid " + uuid + " not found"));
        userEntityRepository.delete(user);
    }

    public UserEntity changePassword(String uuid, String oldPassword, String newPassword) {
        log.info("Changing password for user with UUID: {}", uuid);
        UserEntity user = getUserByUuid(uuid);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Old password is incorrect for user {}", user.getUsername());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Old password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        return saveUserEntity(user);
    }

    public UserEntity changePasswordByAdmin(String uuid, String newPassword) {
        log.info("Admin changing password for user with UUID: {}", uuid);
        UserEntity user = getUserByUuid(uuid);

        user.setPassword(passwordEncoder.encode(newPassword));
        return saveUserEntity(user);
    }

    public UserEntity changeRole(String uuid, Role newRole) {
        log.info("Changing role for user with UUID: {} to {}", uuid, newRole);
        if (newRole.equals(Role.OWNER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign OWNER role");
        }
        UserEntity user = getUserByUuid(uuid);
        user.setRole(newRole);
        return saveUserEntity(user);
    }

    public UserEntity updateDockerLimits(String uuid, DockerHardwareLimits dockerHardwareLimits) {
        log.info("Updating docker limits for user with UUID: {}", uuid);
        UserEntity user = getUserByUuid(uuid);

        user.setDockerHardwareLimits(dockerHardwareLimits);
        return saveUserEntity(user);
    }

    public boolean existsByUsername(String username) {
        return userEntityRepository.existsByUsernameIgnoreCase(username);
    }

    public boolean existsByUsernameIgnoreCase(String username) {
        return userEntityRepository.existsByUsernameIgnoreCase(username);
    }

    public List<UserEntity> getAdminUsers() {
        return userEntityRepository.findByRoleIn(List.of(Role.OWNER, Role.ADMIN));
    }
}
