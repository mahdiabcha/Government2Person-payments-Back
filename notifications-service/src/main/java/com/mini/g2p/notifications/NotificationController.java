package com.mini.g2p.notifications;

import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
  private final NotificationRepository repo;
  public NotificationController(NotificationRepository r){ this.repo = r; }

  @GetMapping("/me")
  public Page<Notification> my(@RequestHeader HttpHeaders headers,
                               @RequestParam(defaultValue="0") int page,
                               @RequestParam(defaultValue="20") int size){
    String user = SecurityHelpers.username(headers);
    Pageable p = PageRequest.of(Math.max(0,page), Math.min(100, Math.max(1,size)));
    return repo.findByUsernameOrderByStatusAscCreatedAtDesc(user, p);
  }

  @GetMapping("/me/count")
  public Map<String, Long> count(@RequestHeader HttpHeaders headers){
    String user = SecurityHelpers.username(headers);
    long n = repo.countByUsernameAndStatus(user, Notification.Status.NEW);
    return Map.of("new", n);
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<?> markRead(@RequestHeader HttpHeaders headers, @PathVariable Long id){
    String user = SecurityHelpers.username(headers);
    var n = repo.findById(id).orElse(null);
    if (n==null || (n.getUsername()!=null && !user.equals(n.getUsername())))
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    n.setStatus(Notification.Status.READ);
    repo.save(n);
    return ResponseEntity.ok(Map.of("status","READ"));
  }
}
