package com.magentamause.cosybackend.repositories;

import com.magentamause.cosybackend.entities.UserInviteEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInviteRepository extends JpaRepository<UserInviteEntity, String> {
    Optional<UserInviteEntity> findBySecretKey(String secretKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM UserInviteEntity i WHERE i.secretKey = :secretKey")
    Optional<UserInviteEntity> findBySecretKeyLocked(@Param("secretKey") String secretKey);

    @Query(
            "SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM UserInviteEntity i WHERE LOWER(i.username) = LOWER(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);
}
