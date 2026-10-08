package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.AuthResponse;
import com.keyStone.Playroom021.dto.LoginRequest;
import com.keyStone.Playroom021.dto.SignupRequest;
import com.keyStone.Playroom021.entity.Customer;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.User;
import com.keyStone.Playroom021.repository.CustomerRepository;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.security.JwtUtil;
import com.keyStone.Playroom021.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        if (request.getRole() == Role.LOCAL_CUSTOMER &&
                (request.getCompanyName() == null || request.getCompanyName().isBlank())) {
            throw new IllegalArgumentException("Company name is required for a customer account");
        }

        User.UserBuilder userBuilder = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole());

        if (request.getRole() == Role.LOCAL_CUSTOMER) {
            Customer customer = customerRepository.save(Customer.builder()
                    .companyName(request.getCompanyName().trim())
                    .contactEmail(request.getEmail())
                    .build());
            userBuilder.customer(customer);
        }

        User user = userBuilder.build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails, user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .companyName(user.getCustomer() != null ? user.getCustomer().getCompanyName() : null)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        String token = jwtUtil.generateToken(userDetails, user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .companyName(user.getCustomer() != null ? user.getCustomer().getCompanyName() : null)
                .build();
    }

    public void logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long expiryMs = jwtUtil.extractExpiration(token).getTime();
            tokenBlacklistService.blacklist(token, expiryMs);
        }
    }
}
