package com.company.flowmanagement.repository;

import com.company.flowmanagement.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserRepository extends MongoRepository<User, String> {

    User findByUsername(String username);

    List<User> findByRole(String role);
}
