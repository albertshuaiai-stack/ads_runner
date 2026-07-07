package com.admire.cars.runner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.admire.cars.runner.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
