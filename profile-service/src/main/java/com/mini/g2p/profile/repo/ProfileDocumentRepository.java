package com.mini.g2p.profile.repo;

import com.mini.g2p.profile.domain.ProfileDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileDocumentRepository extends JpaRepository<ProfileDocument, Long> {
  List<ProfileDocument> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
  Optional<ProfileDocument> findFirstByOwnerUsernameAndTypeOrderByCreatedAtDesc(String ownerUsername, String type);
  Optional<ProfileDocument> findByIdAndOwnerUsername(Long id, String ownerUsername);
  List<ProfileDocument> findByOwnerUsernameIgnoreCaseOrderByCreatedAtDesc(String ownerUsername);
  Optional<ProfileDocument> findFirstByOwnerUsernameAndTypeIgnoreCaseOrderByCreatedAtDesc(String ownerUsername, String type);
  Optional<ProfileDocument> findByIdAndOwnerUsernameIgnoreCase(Long id, String ownerUsername);
}

