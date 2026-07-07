package com.admire.cars.runner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.admire.cars.runner.entity.User;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserName(String userName);
    Optional<User> findByUserEmail(String userEmail);
    Optional<User> findByUserPhoneNumber(String userPhoneNumber);
    List<User> findByStatus(String status);
}
