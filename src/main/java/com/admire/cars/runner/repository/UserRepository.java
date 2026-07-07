package com.admire.cars.runner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.admire.cars.runner.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
