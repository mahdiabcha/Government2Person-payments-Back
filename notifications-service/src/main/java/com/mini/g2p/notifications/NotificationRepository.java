package com.mini.g2p.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  @Query("""
      select n from Notification n
      where (n.recipientUsername = :u)
         or (n.recipientUsername is null and
             (n.audienceRole is null or (:isAdmin = true and n.audienceRole = 'ADMIN')))
      order by n.createdAt desc
      """)
  Page<Notification> feedForUser(@Param("u") String username,
                                 @Param("isAdmin") boolean isAdmin,
                                 Pageable pageable);

  @Query("""
      select count(n) from Notification n
      where n.status = :status and (
        n.recipientUsername = :u
        or (n.recipientUsername is null and
            (n.audienceRole is null or (:isAdmin = true and n.audienceRole = 'ADMIN')))
      )
      """)
  long countNewForUser(@Param("u") String username,
                       @Param("isAdmin") boolean isAdmin,
                       @Param("status") NotificationStatus status);
}
