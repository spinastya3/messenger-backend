package com.example.messenger.repository;

import com.example.messenger.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByDeviceId(String deviceId);

    List<UserDevice> findAllByUserId(Long userId);

    void deleteByDeviceId(String deviceId);
}