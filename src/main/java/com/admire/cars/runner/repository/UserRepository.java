package com.admire.cars.runner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.admire.cars.runner.entity.User;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUserName(String userName);
    Optional<User> findByUserEmail(String userEmail);
    Optional<User> findByUserPhoneNumber(String userPhoneNumber);
    Optional<User> findByApiKey(String apiKey);
    List<User> findByStatus(String status);
}
