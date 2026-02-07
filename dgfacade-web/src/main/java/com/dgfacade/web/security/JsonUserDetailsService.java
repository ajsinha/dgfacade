/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.web.security;

import com.dgfacade.common.config.DGFacadeProperties;
import com.dgfacade.common.model.DGUser;
import com.dgfacade.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class JsonUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(JsonUserDetailsService.class);
    private final DGFacadeProperties properties;
    private final Map<String, DGUser> users = new ConcurrentHashMap<>();

    public JsonUserDetailsService(DGFacadeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        loadUsers();
    }

    public void loadUsers() {
        try {
            Path path = Paths.get(properties.getSecurity().getUsersFile());
            if (Files.exists(path)) {
                String json = Files.readString(path);
                List<DGUser> userList = JsonUtils.fromJson(json, new TypeReference<List<DGUser>>() {});
                users.clear();
                userList.forEach(u -> users.put(u.getUserId(), u));
                log.info("Loaded {} users from {}", users.size(), path);
            } else {
                log.warn("Users file not found: {}. Using defaults.", path);
                createDefaultUser();
            }
        } catch (Exception e) {
            log.error("Failed to load users", e);
            createDefaultUser();
        }
    }

    private void createDefaultUser() {
        DGUser admin = new DGUser();
        admin.setUserId("admin");
        admin.setPassword("admin123");
        admin.setDisplayName("Administrator");
        admin.setRoles(List.of("ADMIN", "USER"));
        users.put("admin", admin);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DGUser user = users.get(username);
        if (user == null || !user.isActive()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());

        return User.builder()
                .username(user.getUserId())
                .password("{noop}" + user.getPassword())
                .authorities(authorities)
                .build();
    }

    public Collection<DGUser> getAllUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    public Optional<DGUser> getUser(String userId) {
        return Optional.ofNullable(users.get(userId));
    }
}
