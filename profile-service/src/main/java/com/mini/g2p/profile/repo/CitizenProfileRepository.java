package com.mini.g2p.profile.repo;

import com.mini.g2p.profile.domain.CitizenProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitizenProfileRepository extends JpaRepository<CitizenProfile, Long> {
  Optional<CitizenProfile> findByUsername(String username);
  boolean existsByUsername(String username);
  Optional<CitizenProfile> findByUsernameIgnoreCase(String username); // <-- add this

}
