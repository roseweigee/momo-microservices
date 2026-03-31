package com.demo.user.service;

import com.demo.user.model.UserEntity;
import com.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserEntity getUserById(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    public UserEntity createUser(String username, String email, String fullName, String phone, String address) {
        UserEntity user = UserEntity.builder()
                .userId(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .address(address)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(user);
        log.info("User created: {}", user.getUserId());
        return user;
    }

    public UserEntity updateUser(String userId, String fullName, String phone, String address) {
        UserEntity user = getUserById(userId);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
