package com.magentamause.cosybackend.services;

import com.magentamause.cosybackend.dtos.entitydtos.UserEntityDto;
import com.magentamause.cosybackend.entities.UserEntity;
import com.magentamause.cosybackend.repositories.UserEntityRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEntityService {

    private final UserEntityRepository userEntityRepository;

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

    public UserEntity getUserByUsername(String username) {
        return userEntityRepository
                .findByUsername(username)
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

    public UserEntityDto convertToDTO(UserEntity user) {
        return UserEntityDto.builder()
                .username(user.getUsername())
                .role(user.getRole())
                .uuid(user.getUuid())
                .build();
    }

    public boolean existsByUsername(String username) {
        return userEntityRepository.existsByUsername(username);
    }
}
