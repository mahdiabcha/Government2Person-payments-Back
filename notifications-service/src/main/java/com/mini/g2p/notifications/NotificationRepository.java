package com.mini.g2p.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Page<Notification> findByUsernameOrderByStatusAscCreatedAtDesc(String username, Pageable pageable);
  long countByUsernameAndStatus(String username, Notification.Status status);
}
