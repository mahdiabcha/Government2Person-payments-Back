package com.mini.g2p.programcatalog.controllers;

import com.mini.g2p.programcatalog.clients.EnrollmentClient;
import com.mini.g2p.programcatalog.domain.CycleState;
import com.mini.g2p.programcatalog.domain.Entitlement;
import com.mini.g2p.programcatalog.domain.EntitlementState;
import com.mini.g2p.programcatalog.repo.EntitlementRepository;
import com.mini.g2p.programcatalog.repo.ProgramCycleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class EntitlementController {

  private final EntitlementRepository entRepo;
  private final ProgramCycleRepository cycles;
  private final EnrollmentClient enrollmentClient;

  public EntitlementController(EntitlementRepository entRepo,
                               ProgramCycleRepository cycles,
                               EnrollmentClient enrollmentClient) {
    this.entRepo = entRepo;
    this.cycles = cycles;
    this.enrollmentClient = enrollmentClient;
  }

  @GetMapping("/cycles/{cycleId}/entitlements")
  public List<Entitlement> list(@PathVariable Long cycleId) {
    return entRepo.findByCycleIdOrderByIdAsc(cycleId);
  }

  public record GenerateItem(String username, Double amount, String currency,
                             LocalDate validFrom, LocalDate validUntil) {}
  public record GenerateReq(List<GenerateItem> items) {}

  @PostMapping("/programs/{programId}/cycles/{cycleId}/entitlements/generate")
  public List<Entitlement> generate(@PathVariable Long programId,
                                    @PathVariable Long cycleId,
                                    @RequestBody GenerateReq req) {
    var c = cycles.findById(cycleId).orElseThrow();
    if (!Objects.equals(c.getProgramId(), programId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cycle does not belong to the given program");
    }
    if (entRepo.countByCycleId(cycleId) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Entitlements already generated for this cycle");
    }

    List<Entitlement> out = new ArrayList<>();
    for (var it : req.items()) {
      var e = new Entitlement();
      e.setProgramId(programId);
      e.setCycleId(cycleId);
      e.setBeneficiaryUsername(it.username());
      e.setAmount(it.amount());
      e.setCurrency(it.currency());
      e.setValidFrom(it.validFrom());
      e.setValidUntil(it.validUntil());
      e.setState(c.getState() == CycleState.APPROVED ? EntitlementState.APPROVED : EntitlementState.DRAFT);
      out.add(entRepo.save(e));
    }
    return out;
  }

  public record PrepareReq(Double amount, String currency,
                           LocalDate validFrom, LocalDate validUntil) {}

  @PostMapping("/programs/{programId}/cycles/{cycleId}/entitlements/prepare-from-enrollments")
  public ResponseEntity<?> prepareFromEnrollments(@RequestHeader HttpHeaders headers,
                                                  @PathVariable Long programId,
                                                  @PathVariable Long cycleId,
                                                  @RequestBody PrepareReq req) {
    var c = cycles.findById(cycleId).orElse(null);
    if (c == null || !Objects.equals(c.getProgramId(), programId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "cycle not found"));
    }

    var usernames = enrollmentClient.getApprovedUsernames(programId, headers);
    if (usernames == null || usernames.isEmpty()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "no approved enrollments"));
    }

    var existing = entRepo.findByCycleIdOrderByIdAsc(cycleId).stream()
        .map(Entitlement::getBeneficiaryUsername)
        .collect(Collectors.toSet());

    int created = 0;
    for (String u : usernames) {
      if (existing.contains(u)) continue;
      var e = new Entitlement();
      e.setProgramId(programId);
      e.setCycleId(cycleId);
      e.setBeneficiaryUsername(u);
      e.setAmount(req.amount());
      e.setCurrency(req.currency());
      e.setValidFrom(req.validFrom());
      e.setValidUntil(req.validUntil());
      e.setState(c.getState() == CycleState.APPROVED ? EntitlementState.APPROVED : EntitlementState.DRAFT);
      entRepo.save(e);
      created++;
    }
    return ResponseEntity.ok(Map.of("added", created, "total", entRepo.countByCycleId(cycleId)));
  }

  // --- NEW: change state of a single entitlement (gateway-friendly path under /cycles/**)
  public record ChangeStateReq(String value) {}

  @PatchMapping("/cycles/{cycleId}/entitlements/{id}/state")
  public Entitlement changeEntitlementState(@PathVariable Long cycleId,
                                            @PathVariable Long id,
                                            @RequestBody ChangeStateReq req) {
    var ent = entRepo.findById(id).orElseThrow();
    if (!Objects.equals(ent.getCycleId(), cycleId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cycleId mismatch");
    }
    final EntitlementState newState;
    try {
      newState = EntitlementState.valueOf(req.value());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid state: " + req.value());
    }
    ent.setState(newState);
    return entRepo.save(ent);
  }

  // --- NEW: approve all entitlements in a cycle (only DRAFT -> APPROVED)
  @PostMapping("/cycles/{cycleId}/entitlements/approve-all")
  public Map<String, Object> approveAllInCycle(@PathVariable Long cycleId) {
    var list = entRepo.findByCycleIdOrderByIdAsc(cycleId);
    int updated = 0;
    for (var e : list) {
      if (e.getState() == EntitlementState.DRAFT) {
        e.setState(EntitlementState.APPROVED);
        entRepo.save(e);
        updated++;
      }
    }
    return Map.of("updated", updated, "total", list.size());
  }

  // Keep this endpoint – internal use by PaymentService
  @GetMapping("/internal/entitlements/approved")
  public java.util.List<com.mini.g2p.programcatalog.dto.EntitlementDtos.ApprovedEntitlement>
  approvedForCycle(@RequestParam Long cycleId) {
    // Ensure cycle exists (gives 404 if not)
    cycles.findById(cycleId).orElseThrow();
    return entRepo.findByCycleIdAndState(cycleId, EntitlementState.APPROVED).stream()
        .map(e -> new com.mini.g2p.programcatalog.dto.EntitlementDtos.ApprovedEntitlement(
            e.getId(), e.getBeneficiaryUsername(), e.getAmount(), e.getCurrency()))
        .toList();
  }
}