package com.example.messenger.repository;

import com.example.messenger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Ищем юзера по имени
    Optional<User> findByUsername(String username);
    // Ищем юзера по email
    Optional<User> findByEmail(String email);
    // Проверяем логин на уникальность
    boolean existsByUsername(String username);
    // Проверяем email на уникальность
    boolean existsByEmail(String email);
    // Ищем зарегестрированного пользователя
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    List<User> searchUsersByUsernameIgnoreCase(@Param("username") String username);
}
