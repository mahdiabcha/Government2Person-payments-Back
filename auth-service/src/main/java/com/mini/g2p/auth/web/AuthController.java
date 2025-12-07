package com.mini.g2p.auth.web;

import com.mini.g2p.auth.dto.AuthDtos.*;
import com.mini.g2p.auth.domain.User;
import com.mini.g2p.auth.repo.UserRepository;
import com.mini.g2p.auth.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authManager;
  private final PasswordEncoder encoder;
  private final UserRepository users;
  private final JwtUtil jwt;

  public AuthController(AuthenticationManager authManager, PasswordEncoder encoder, UserRepository users, JwtUtil jwt) {
    this.authManager = authManager;
    this.encoder = encoder;
    this.users = users;
    this.jwt = jwt;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest in) {
    if (users.findByUsername(in.username).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("Username already exists"));
    }

    User u = new User();
    u.setUsername(in.username);
    u.setNationalId(in.nationalId);
    u.setPasswordHash(encoder.encode(in.password));
    u.setCreatedAt(Instant.now());

    // role mapping: AGENT -> ADMIN (+CITIZEN), else CITIZEN
    Set<String> roles = new HashSet<>();
    String role = (in.role == null ? "" : in.role.trim().toUpperCase(Locale.ROOT));
    if ("AGENT".equals(role) || "ADMIN".equals(role)) {
      roles.add("ADMIN");
      roles.add("CITIZEN"); 
    } else {
      roles.add("CITIZEN");
    }
    u.setRoles(roles);

    users.save(u);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message","registered"));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest in) {
    try {
      authManager.authenticate(new UsernamePasswordAuthenticationToken(in.username, in.password));
    } catch (BadCredentialsException ex) {
      return ResponseEntity.status(401).body(new ErrorResponse("Bad credentials"));
    }
    var u = users.findByUsername(in.username).orElseThrow();
    String token = jwt.generateToken(u.getUsername(), Map.of("roles", u.getRoles()));
    return ResponseEntity.ok(new AuthResponse(token));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated())
      return ResponseEntity.status(401).body(new ErrorResponse("Unauthorized"));
    var u = users.findByUsername(authentication.getName()).orElseThrow();
    return ResponseEntity.ok(Map.of("username", u.getUsername(), "roles", u.getRoles()));
  }
}
