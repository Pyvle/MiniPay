package com.minipay.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>{ 
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countBySystemRoleAndState(SystemRole systemRole, UserState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param ("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.mainRole.id = :roleId order by u.id")
    List<User> findByMainRoleIdForUpdate(@Param("roleId") Long roleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    List<User> findAllByIdsForUpdate(@Param("ids") List<Long> ids);
}
