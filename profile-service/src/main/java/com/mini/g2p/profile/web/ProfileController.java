package com.mini.g2p.profile.web;

import com.mini.g2p.profile.domain.CitizenProfile;
import com.mini.g2p.profile.domain.CitizenProfile.PaymentMethod;
import com.mini.g2p.profile.domain.ProfileDocument;
import com.mini.g2p.profile.dto.ProfileRequest;
import com.mini.g2p.profile.repo.CitizenProfileRepository;
import com.mini.g2p.profile.repo.ProfileDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

  private final CitizenProfileRepository profiles;
  private final ProfileDocumentRepository docs;

  /* =========================
     Helpers
     ========================= */
private static String sanitize(String s) {
  if (s == null || s.isBlank()) return "document";

  // Build a safe filename by hand (no regex). Replace control chars and invalid filename chars.
  String invalid = "\\/:*?\"<>|"; // Windows-invalid + path separators
  StringBuilder sb = new StringBuilder(s.length());
  for (int i = 0; i < s.length(); i++) {
    char c = s.charAt(i);
    if (c <= 31 || c == 127 || invalid.indexOf(c) >= 0) {
      sb.append('_');
    } else {
      sb.append(c);
    }
  }

  // Trim, collapse underscores, and ensure not blank
  String safe = sb.toString().trim().replaceAll("_+", "_");
  if (safe.isBlank()) safe = "document";
  return safe;
}



  private static String contentDisposition(String filename, boolean inline) {
    String safe = sanitize(filename);
    String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    String type = inline ? "inline" : "attachment";
    return type + "; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
  }

  private static String requireUser(HttpHeaders headers) {
    String u = SecurityHelpers.currentUser(headers);
    if (u == null || u.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user");
    }
    return u;
  }

  private static void requireAdmin(HttpHeaders headers) {
    if (!SecurityHelpers.hasRole(headers, "ADMIN")) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN only");
    }
  }

  private static Map<String, Object> toDto(CitizenProfile p) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("firstName", p.getFirstName());
    dto.put("lastName", p.getLastName());
    dto.put("gender", p.getGender());
    dto.put("dateOfBirth", p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : null);
    dto.put("governorate", p.getGovernorate());
    dto.put("district", p.getDistrict());
    dto.put("householdSize", p.getHouseholdSize());
    dto.put("incomeMonthly", p.getIncomeMonthly());
    dto.put("kycVerified", p.getKycVerified());
    dto.put("paymentMethod", p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "NONE");
    dto.put("bankName", p.getBankName());
    dto.put("iban", p.getIban());
    dto.put("accountHolder", p.getAccountHolder());
    dto.put("walletProvider", p.getWalletProvider());
    dto.put("walletNumber", p.getWalletNumber());
    return dto;
  }

  private static void apply(ProfileRequest r, CitizenProfile p) {
    if (r == null) return;
    if (r.firstName != null) p.setFirstName(r.firstName);
    if (r.lastName != null) p.setLastName(r.lastName);
    if (r.gender != null) p.setGender(r.gender);
    if (r.dateOfBirth != null && !r.dateOfBirth.isBlank()) {
      try { p.setDateOfBirth(LocalDate.parse(r.dateOfBirth.trim())); }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dateOfBirth (expected yyyy-MM-dd)"); }
    }
    if (r.governorate != null) p.setGovernorate(r.governorate);
    if (r.district != null) p.setDistrict(r.district);
    if (r.householdSize != null) p.setHouseholdSize(r.householdSize);
    if (r.incomeMonthly != null) p.setIncomeMonthly(r.incomeMonthly);
    if (r.kycVerified != null) p.setKycVerified(r.kycVerified);
    if (r.paymentMethod != null) {
      String pm = r.paymentMethod.trim().toUpperCase(Locale.ROOT);
      try { p.setPaymentMethod(PaymentMethod.valueOf(pm)); }
      catch (Exception ignored) { p.setPaymentMethod(PaymentMethod.NONE); }
    }
    if (r.bankName != null) p.setBankName(r.bankName);
    if (r.iban != null) p.setIban(r.iban);
    if (r.accountHolder != null) p.setAccountHolder(r.accountHolder);
    if (r.walletProvider != null) p.setWalletProvider(r.walletProvider);
    if (r.walletNumber != null) p.setWalletNumber(r.walletNumber);
  }

  /* =========================
     Self profile
     ========================= */

  @GetMapping("/profiles/me")
  public ResponseEntity<Map<String, Object>> me(@RequestHeader HttpHeaders headers) {
    String u = requireUser(headers);
    return profiles.findByUsername(u)
        .map(p -> ResponseEntity.ok(toDto(p)))
        .orElseGet(() -> ResponseEntity.ok(Collections.emptyMap()));
  }

  @PostMapping("/profiles/me")
  @Transactional
  public ResponseEntity<Map<String, Object>> save(@RequestHeader HttpHeaders headers, @RequestBody ProfileRequest body) {
    String u = requireUser(headers);
    CitizenProfile p = profiles.findByUsername(u).orElseGet(() -> {
      CitizenProfile np = new CitizenProfile();
      np.setUsername(u);
      np.setPaymentMethod(PaymentMethod.NONE);
      return np;
    });
    apply(body, p);
    profiles.save(p);
    return ResponseEntity.ok(toDto(p));
  }

  /* =========================
     Self documents
     ========================= */

  public record DocMeta(Long id, String type, String filename, String contentType, Long size, String createdAt) {}

  @GetMapping("/profiles/me/documents")
  public List<DocMeta> myDocs(@RequestHeader HttpHeaders headers) {
    String u = requireUser(headers);
    return docs.findByOwnerUsernameOrderByCreatedAtDesc(u).stream()
        .map(d -> new DocMeta(d.getId(), d.getType(), d.getFilename(), d.getContentType(), d.getSize(),
            d.getCreatedAt()!=null? d.getCreatedAt().toString(): null))
        .collect(Collectors.toList());
  }

  @PostMapping(path = "/profiles/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Map<String, Object> upload(@RequestHeader HttpHeaders headers,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "type", required = false, defaultValue = "OTHER") String type) {
    String u = requireUser(headers);
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
    }
    try {
      ProfileDocument d = new ProfileDocument();
      d.setOwnerUsername(u);
      d.setType(type == null ? "OTHER" : type.trim().toUpperCase(Locale.ROOT));
      d.setContentType(Optional.ofNullable(file.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE));
      d.setSize(file.getSize());
      d.setFilename(Optional.ofNullable(file.getOriginalFilename()).map(ProfileController::sanitize).orElse("document"));
      d.setData(file.getBytes());
      docs.save(d);
      return Map.of("id", d.getId(), "status", "OK");
    } catch (Exception e) {
      log.warn("Upload failed", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed");
    }
  }

  @GetMapping("/profiles/me/documents/{id}/download")
  public ResponseEntity<byte[]> downloadMine(@RequestHeader HttpHeaders headers, @PathVariable Long id) {
    String u = requireUser(headers);
    ProfileDocument d = docs.findByIdAndOwnerUsername(id, u)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(Optional.ofNullable(d.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(d.getFilename(), false))
        .contentLength(Optional.ofNullable(d.getSize()).orElseGet(() -> d.getData()!=null ? (long)d.getData().length : 0L))
        .body(d.getData());
  }

  @DeleteMapping("/profiles/me/documents/{id}")
  @Transactional
  public Map<String, Object> deleteMine(@RequestHeader HttpHeaders headers, @PathVariable Long id) {
    String u = requireUser(headers);
    ProfileDocument d = docs.findByIdAndOwnerUsername(id, u)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    docs.delete(d);
    return Map.of("status", "DELETED");
  }

  /* =========================
     Admin profile + documents (read-only)
     ========================= */



  @GetMapping({"/profiles/admin/{username:.+}", "/admin/profiles/{username:.+}"})
  public ResponseEntity<Map<String, Object>> adminGetProfile(@RequestHeader HttpHeaders headers, @PathVariable String username) {
    requireAdmin(headers);
    String owner = resolveOwnerCanonical(username);
    return profiles.findByUsernameIgnoreCase(owner)
        .map(p -> ResponseEntity.ok(toDto(p)))                // <-- plain Profile-like map
        .orElseGet(() -> ResponseEntity.ok(Collections.emptyMap())); // empty {} if none
  }




  private String resolveOwnerCanonical(String username) {
  String u = username == null ? "" : username.trim();
  return profiles.findByUsernameIgnoreCase(u).map(CitizenProfile::getUsername).orElse(u);
}

  @GetMapping({"/profiles/admin/{username:.+}/documents", "/admin/profiles/{username:.+}/documents"})
  public List<DocMeta> adminListDocs(@RequestHeader HttpHeaders headers, @PathVariable String username) {
    requireAdmin(headers);
    String owner = resolveOwnerCanonical(username);
    return docs.findByOwnerUsernameIgnoreCaseOrderByCreatedAtDesc(owner).stream()
        .map(d -> new DocMeta(d.getId(), d.getType(), d.getFilename(), d.getContentType(), d.getSize(),
            d.getCreatedAt()!=null? d.getCreatedAt().toString(): null))
        .collect(Collectors.toList());
  }

  @GetMapping({"/profiles/admin/{username:.+}/documents/latest", "/admin/profiles/{username:.+}/documents/latest"})
  public ResponseEntity<DocMeta> adminLatestByType(@RequestHeader HttpHeaders headers,
                                                  @PathVariable String username,
                                                  @RequestParam("type") String type) {
    requireAdmin(headers);
    String owner = resolveOwnerCanonical(username);
    String t = type == null ? "OTHER" : type.trim().toUpperCase(Locale.ROOT);
    return docs.findFirstByOwnerUsernameAndTypeIgnoreCaseOrderByCreatedAtDesc(owner, t)
        .map(d -> ResponseEntity.ok(new DocMeta(d.getId(), d.getType(), d.getFilename(), d.getContentType(), d.getSize(),
            d.getCreatedAt()!=null? d.getCreatedAt().toString(): null)))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No document of requested type"));
  }

  @GetMapping({"/profiles/admin/{username:.+}/documents/{id}/download", "/admin/profiles/{username:.+}/documents/{id}/download"})
  public ResponseEntity<byte[]> adminDownload(@RequestHeader HttpHeaders headers, @PathVariable String username, @PathVariable Long id) {
    requireAdmin(headers);
    String owner = resolveOwnerCanonical(username);
    ProfileDocument d = docs.findByIdAndOwnerUsernameIgnoreCase(id, owner)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(Optional.ofNullable(d.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(d.getFilename(), false))
        .contentLength(Optional.ofNullable(d.getSize()).orElseGet(() -> d.getData()!=null ? (long)d.getData().length : 0L))
        .body(d.getData());
  }

  @GetMapping({"/profiles/admin/{username:.+}/documents/{id}/inline", "/admin/profiles/{username:.+}/documents/{id}/inline"})
  public ResponseEntity<byte[]> adminInline(@RequestHeader HttpHeaders headers, @PathVariable String username, @PathVariable Long id) {
    requireAdmin(headers);
    String owner = resolveOwnerCanonical(username);
    ProfileDocument d = docs.findByIdAndOwnerUsernameIgnoreCase(id, owner)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(Optional.ofNullable(d.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(d.getFilename(), true))
        .contentLength(Optional.ofNullable(d.getSize()).orElseGet(() -> d.getData()!=null ? (long)d.getData().length : 0L))
        .body(d.getData());
  }

  }
