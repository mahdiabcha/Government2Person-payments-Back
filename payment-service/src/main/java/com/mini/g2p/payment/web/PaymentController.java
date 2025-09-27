package com.mini.g2p.payment.web;

import com.mini.g2p.payment.clients.ProgramClient;
import com.mini.g2p.payment.domain.*;
import com.mini.g2p.payment.repo.*;
import com.mini.g2p.payment.amqp.RabbitConfig;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentBatchRepository batches;
  private final PaymentInstructionRepository instr;
  private final AmqpTemplate amqp;
  private final ProgramClient programClient;

  public PaymentController(PaymentBatchRepository b, PaymentInstructionRepository i, AmqpTemplate a, ProgramClient pc) {
    this.batches=b; this.instr=i; this.amqp=a; this.programClient=pc;
  }

  // ---- helpers ----
  private boolean isAdmin(HttpHeaders headers){
    String rh=headers.getFirst("X-Auth-Roles");
    if (rh!=null) for(String r: rh.split("[,\\s]+")) if (r.equalsIgnoreCase("ADMIN")||r.equalsIgnoreCase("ROLE_ADMIN")) return true;
    String auth=headers.getFirst(HttpHeaders.AUTHORIZATION);
    if (auth!=null && auth.startsWith("Bearer ")) try{
      String[] p=auth.substring(7).split("\\."); if (p.length>=2){
        String json=new String(java.util.Base64.getUrlDecoder().decode(p[1]));
        if (json.contains("\"roles\"") && (json.contains("ADMIN")||json.contains("ROLE_ADMIN"))) return true;
      }
    }catch(Exception ignored){}
    return false;
  }

  // ===== Manual batch =====
  public record CreateBatchReq(Long programId, Double amount, String currency, List<String> beneficiaries) {}

  @PostMapping("/batches")
  public ResponseEntity<?> createManual(@RequestHeader HttpHeaders headers, @RequestBody CreateBatchReq req) {
    if (!isAdmin(headers)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","ADMIN only"));
    if (req.programId()==null || req.amount()==null || req.amount()<=0 || req.currency()==null || req.currency().isBlank())
      return ResponseEntity.badRequest().body(Map.of("error","programId, amount>0, currency required"));
    List<String> bens = (req.beneficiaries()==null? List.<String>of() : req.beneficiaries()).stream()
        .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().collect(Collectors.toList());
    if (bens.isEmpty()) return ResponseEntity.status(409).body(Map.of("error","no beneficiaries"));

    var batch = new PaymentBatch(); batch.setProgramId(req.programId());
    batches.save(batch);
    for (String u : bens) {
      var pi = new PaymentInstruction();
      pi.setBatchId(batch.getId()); pi.setBeneficiaryUsername(u);
      pi.setAmount(req.amount()); pi.setCurrency(req.currency().trim().toUpperCase());
      instr.save(pi);
    }
    batch.setTotalCount(bens.size()); batches.save(batch);
    return ResponseEntity.ok(Map.of("totalCount",batch.getTotalCount(),"batchId",batch.getId()));
  }


  @PostMapping("/batches/{id}/dispatch")
  public ResponseEntity<?> dispatch(@RequestHeader HttpHeaders headers, @PathVariable Long id) {
    if (!isAdmin(headers)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","ADMIN only"));
    }

    var b = batches.findById(id).orElseThrow();
    b.setStatus(PaymentBatch.Status.PROCESSING);
    batches.save(b);

    instr.findByBatchId(id).forEach(pi -> {
      BigDecimal amount = toBigDecimal(pi.getAmount()); // <- ensure BigDecimal for DTO
      var msg = new com.mini.g2p.payment.dto.PaymentInstructionMsg(
          pi.getId(),
          b.getProgramId(),
          pi.getBeneficiaryUsername(), // <- username
          amount,
          pi.getCurrency()
      );
      amqp.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_INSTR, msg);
      pi.setStatus(PaymentInstruction.Status.SENT);
      instr.save(pi);
    });

    return ResponseEntity.ok(Map.of("status","DISPATCHED"));
  }

  private static BigDecimal toBigDecimal(Object v) {
    if (v == null) return null;
    if (v instanceof BigDecimal bd) return bd;
    return new BigDecimal(v.toString()); // avoids binary double rounding
  }

  @GetMapping("/batches/{id}")
  public Map<String,Object> get(@PathVariable Long id) {
    var b = batches.findById(id).orElseThrow();
    return Map.of("batch", b, "instructions", instr.findByBatchId(id));
  }

  @GetMapping("/batches")
  public List<PaymentBatch> list(){ return batches.findAll(); }

  // ===== Batch from cycle (APPROVED entitlements) =====
  @PostMapping("/batches/from-cycle")
  public ResponseEntity<?> createFromCycle(@RequestHeader HttpHeaders headers, @RequestParam Long cycleId, @RequestParam Long programId) {
    if (!isAdmin(headers)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","ADMIN only"));

    var ci = programClient.getCycle(cycleId);
    if (ci==null) return ResponseEntity.status(404).body(Map.of("error","cycle not found"));
    if (!Objects.equals(ci.programId(), programId)) return ResponseEntity.status(409).body(Map.of("error","cycle.programId mismatch"));

    var existing = batches.findAll().stream().filter(b -> Objects.equals(b.getCycleId(), cycleId)).findFirst();
    if (existing.isPresent()) {
      var b = existing.get();
      return ResponseEntity.ok(Map.of("totalCount", b.getTotalCount(), "batchId", b.getId(), "existing", true));
    }

    var approved = programClient.getApprovedEntitlements(cycleId);
    if (approved==null || approved.isEmpty()) return ResponseEntity.status(409).body(Map.of("error","no approved entitlements"));

    var batch = new PaymentBatch(); batch.setProgramId(programId); batch.setCycleId(cycleId);
    batches.save(batch);

    approved.forEach(e -> {
      var pi = new PaymentInstruction();
      pi.setBatchId(batch.getId());
      pi.setBeneficiaryUsername(e.username());
      pi.setAmount(e.amount());
      pi.setCurrency(e.currency()!=null ? e.currency().trim().toUpperCase() : "TND");
      instr.save(pi);
    });
    batch.setTotalCount(approved.size()); batches.save(batch);
    return ResponseEntity.ok(Map.of("totalCount", batch.getTotalCount(), "batchId", batch.getId()));
  }
}