package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    @Query(
            "SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u WHERE LOWER(u.username) = LOWER(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    List<UserEntity> findByRoleIn(List<UserEntity.Role> roles);
}
