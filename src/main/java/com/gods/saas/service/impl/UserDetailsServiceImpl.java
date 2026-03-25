package com.gods.saas.service.impl;

import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        System.out.println("LOAD USER EMAIL => " + user.getEmail());
        System.out.println("LOAD USER HASH => " + user.getPasswordHash());
        System.out.println("LOAD USER GETPASSWORD => " + user.getPassword());

        return user;
    }
}