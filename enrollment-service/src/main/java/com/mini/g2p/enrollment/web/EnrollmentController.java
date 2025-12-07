package com.mini.g2p.enrollment.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mini.g2p.enrollment.domain.Enrollment;
import com.mini.g2p.enrollment.dto.BeneficiaryDtos.BeneficiaryItem;
import com.mini.g2p.enrollment.repo.EnrollmentRepository;
import com.mini.g2p.enrollment.service.EligibilityService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import com.mini.g2p.enrollment.client.NotificationsClient;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class EnrollmentController {

  private final EnrollmentRepository repo;
  private final EligibilityService eligibility;
  private final WebClient programClient;
  private final WebClient profileClient;
  private final ObjectMapper M = new ObjectMapper();

  // NEW
  private final NotificationsClient notifications;

  public EnrollmentController(EnrollmentRepository repo, EligibilityService eligibility,
                              @Qualifier("programClient") WebClient programClient,
                              @Qualifier("profileClient") WebClient profileClient,
                              NotificationsClient notifications) {             
    this.repo = repo;
    this.eligibility = eligibility;
    this.programClient = programClient;
    this.profileClient = profileClient;
    this.notifications = notifications;                                    
  }


 ////helpers///
  private JsonNode fetchProgram(long programId, HttpHeaders headers) {
    return programClient.get().uri("/programs/{id}", programId)
        .headers(h -> forward(headers, h))
        .retrieve().bodyToMono(JsonNode.class).blockOptional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
  }

  private JsonNode fetchMyProfile(HttpHeaders headers) {
    return profileClient.get().uri("/profiles/me").headers(h -> forward(headers, h))
        .retrieve().bodyToMono(JsonNode.class).blockOptional()
        .orElse(M.createObjectNode());
  }
  private void forward(HttpHeaders from, HttpHeaders to){
    for (String k : List.of("Authorization","X-Auth-User","X-Auth-Roles")) {
      String v = from.getFirst(k); if (v!=null) to.set(k, v);
    }
  }
  private boolean nonEmpty(Object o){ return o!=null && !String.valueOf(o).isBlank(); }

private boolean profileComplete(JsonNode p){
  boolean hasDob =
      (p.hasNonNull("birthDate")    && !String.valueOf(p.get("birthDate").asText("")).isBlank())
   || (p.hasNonNull("dateOfBirth") && !String.valueOf(p.get("dateOfBirth").asText("")).isBlank());

  return nonEmpty(p.get("firstName"))
      && nonEmpty(p.get("lastName"))
      && hasDob
      && nonEmpty(p.get("governorate"))
      && p.hasNonNull("householdSize")
      && p.hasNonNull("incomeMonthly")
      && p.hasNonNull("kycVerified")
      && p.get("kycVerified").asBoolean(false);
}
/////helpers end ////

  @GetMapping("/programs/{programId}/eligibility/check")
  public Map<String,Object> check(@RequestHeader HttpHeaders headers, @PathVariable long programId){
    JsonNode profile = fetchMyProfile(headers);
    JsonNode program = fetchProgram(programId, headers);
    String rules = program.path("rulesJson").asText("{}");
    var ctx = eligibility.contextFromProfile(profile);
    var res = eligibility.evaluate(ctx, rules);
    return Map.of("eligible", res.eligible, "reason", res.reason);
  }

  // ---------- Enroll ----------
@PostMapping("/programs/{programId}/enroll")
public ResponseEntity<?> enroll(@RequestHeader HttpHeaders headers, @PathVariable long programId) {
  String user = SecurityHelpers.currentUser(headers);
  if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user");

  JsonNode program = fetchProgram(programId, headers);
  String state = program.path("state").asText("DRAFT");
  if (!"ACTIVE".equalsIgnoreCase(state)) throw new ResponseStatusException(HttpStatus.CONFLICT, "program not ACTIVE");

  JsonNode profile = fetchMyProfile(headers);
  if (!profileComplete(profile)) throw new ResponseStatusException(HttpStatus.CONFLICT, "profile incomplete");

  String rules = program.path("rulesJson").asText("{}");
  var ctx = eligibility.contextFromProfile(profile);
  var res = eligibility.evaluate(ctx, rules);
  if (!res.eligible) throw new ResponseStatusException(HttpStatus.CONFLICT, "not eligible: " + res.reason);

  boolean exists = repo.existsByProgramIdAndCitizenUsernameAndStatusIn(programId, user,
      List.of(Enrollment.Status.PENDING, Enrollment.Status.APPROVED));
  if (exists) return ResponseEntity.status(409).body(Map.of("error","already enrolled (pending or approved)"));

  Enrollment e = new Enrollment();
  e.setProgramId(programId); e.setCitizenUsername(user);
  e.setStatus(Enrollment.Status.PENDING);
  e.setEligibilityPassed(true); e.setEligibilityReason("OK"); e.setEligibilityCheckedAt(Instant.now());
  e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
  repo.save(e);

  // --- notify: enrollment.requested (best-effort)
  try {
    String programName = null;
    if (program != null && program.hasNonNull("name")) {
      String n = program.path("name").asText(null);
      if (n != null && !n.isBlank()) programName = n;
    }
    notifications.enrollmentRequested(programId, programName, user);
  } catch (Exception ex) {
    System.err.println("Failed to emit enrollment.requested for program "
        + programId + " user " + user + ": " + ex.getMessage());
  }

  return ResponseEntity.ok(Map.of("id", e.getId(), "status", e.getStatus()));
}


  // ---------- My enrollments ----------
@GetMapping("/enrollments/my")
public List<Map<String,Object>> my(@RequestHeader HttpHeaders headers){
  String user = SecurityHelpers.currentUser(headers);
  if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user");

  return repo.findByCitizenUsernameOrderByCreatedAtDesc(user).stream()
      .<Map<String,Object>>map(e -> Map.<String,Object>of(
          "id", e.getId(),
          "PrgramName", e.getProgramName(),
          "status", e.getStatus(),
          "eligibilityPassed", e.isEligibilityPassed(),
          "eligibilityReason", e.getEligibilityReason(),
          "createdAt", e.getCreatedAt(),
          "updatedAt", e.getUpdatedAt()
      ))
      .toList(); // ou .collect(Collectors.toList()) si tu préfères
}


// ---------- Beneficiaries (admin) ----------
@GetMapping("/programs/{programId}/beneficiaries")
public List<BeneficiaryItem> beneficiaries(@RequestHeader HttpHeaders headers,
                                           @PathVariable long programId,
                                           @RequestParam(value="status", required=false) String status) {
  if (!SecurityHelpers.hasRole(headers,"ADMIN"))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN only");

  List<Enrollment.Status> filter = null;
  if (status != null && !status.isBlank()) {
    try { filter = List.of(Enrollment.Status.valueOf(status.trim().toUpperCase())); }
    catch (Exception ignored) { return List.of(); }
  }

  var list = (filter == null)
      ? repo.findByProgramIdOrderByCreatedAtDesc(programId)
      : repo.findByProgramIdAndStatusOrderByCreatedAtDesc(programId, filter.get(0));

  return list.stream()
      .map(e -> new BeneficiaryItem(
          e.getId(),
          e.getProgramId(),
          e.getCitizenUsername(),      // <-- always provides 'username' to the UI
          e.getStatus().name(),
          e.getEligibilityReason(),
          e.getCreatedAt()
      ))
      .toList();
}


  // ---------- Approve / Reject ----------
 @PatchMapping("/enrollments/{id}/approve")
public Map<String,Object> approve(@RequestHeader HttpHeaders h, @PathVariable long id){
  if (!SecurityHelpers.hasRole(h,"ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN only");
  var e = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  e.setStatus(Enrollment.Status.APPROVED);
  e.setUpdatedAt(Instant.now());
  repo.save(e);

  // --- notify: status changed -> APPROVED (best-effort)
  try {
    notifications.enrollmentStatusChanged(
        e.getId(), e.getProgramId(), e.getCitizenUsername(), "APPROVED", null
    );
  } catch (Exception ex) {
    System.err.println("Failed to emit enrollment status change (APPROVED) for "
        + e.getId() + ": " + ex.getMessage());
  }

  return Map.of("status","APPROVED");
}


@PatchMapping("/enrollments/{id}/reject")
public Map<String,Object> reject(@RequestHeader HttpHeaders h, @PathVariable long id,
                                 @RequestBody(required=false) Map<String,Object> body){
  if (!SecurityHelpers.hasRole(h,"ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN only");
  var e = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  e.setStatus(Enrollment.Status.REJECTED);
  if (body!=null && body.get("note")!=null) e.setEligibilityReason(String.valueOf(body.get("note")));
  e.setUpdatedAt(Instant.now());
  repo.save(e);

  // --- notify: status changed -> REJECTED (best-effort)
  try {
    notifications.enrollmentStatusChanged(
        e.getId(), e.getProgramId(), e.getCitizenUsername(),
        "REJECTED", e.getEligibilityReason()
    );
  } catch (Exception ex) {
    System.err.println("Failed to emit enrollment status change (REJECTED) for "
        + e.getId() + ": " + ex.getMessage());
  }

  return Map.of("status","REJECTED");
}

}