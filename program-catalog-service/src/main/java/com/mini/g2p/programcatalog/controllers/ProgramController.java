package com.mini.g2p.programcatalog.controllers;

import com.mini.g2p.programcatalog.domain.Program;
import com.mini.g2p.programcatalog.domain.ProgramState;
import com.mini.g2p.programcatalog.repo.ProgramRepository;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class ProgramController {
  private final ProgramRepository programs;
  public ProgramController(ProgramRepository programs){ this.programs=programs; }

  @PostMapping("/programs")
  public ResponseEntity<?> create(@RequestBody Program p){ return ResponseEntity.ok(programs.save(p)); }

  @GetMapping("/programs")
  public java.util.List<Program> list() {
    return programs.findAll();
  }

  @GetMapping("/programs/{id}")
  public ResponseEntity<?> get(@PathVariable Long id){
    return programs.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record ChangeStateReq(ProgramState value){}
  @PatchMapping("/programs/{id}/state")
  public ResponseEntity<?> changeState(@PathVariable Long id, @RequestBody ChangeStateReq req){
    var p = programs.findById(id).orElse(null);
    if (p==null) return ResponseEntity.notFound().build();
    var from = p.getState(); var to = req.value();
    if (!allowed(from,to)) return ResponseEntity.status(409).body(Map.of("error","transition not allowed"));
    if (from==ProgramState.DRAFT && to==ProgramState.ACTIVE) {
      if (p.getRulesJson()==null || p.getRulesJson().isBlank()) return ResponseEntity.status(409).body(Map.of("error","rulesJson required to activate"));
    }
    p.setState(to); return ResponseEntity.ok(programs.save(p));
  }


  @PatchMapping("/{id}")
public ResponseEntity<Program> updateProgram(
    @PathVariable Long id,
    @RequestBody ProgramUpdateDto body
) {
  Program p = programs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));

  if (body.name != null && !body.name.isBlank()) p.setName(body.name.trim());
  if (body.description != null) p.setDescription(body.description.trim());

  if (body.rulesJson != null) {
    // defensive size limit (DB column was ~8000)
    if (body.rulesJson.length() > 8000) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "rulesJson too large (max 8000 chars)");
    }
    p.setRulesJson(body.rulesJson.trim());
  }

  programs.save(p);
  return ResponseEntity.ok(p);
}

  private boolean allowed(ProgramState from, ProgramState to){
    return switch (from){
      case DRAFT -> to==ProgramState.ACTIVE || to==ProgramState.ARCHIVED;
      case ACTIVE -> to==ProgramState.INACTIVE || to==ProgramState.ARCHIVED;
      case INACTIVE -> to==ProgramState.ACTIVE || to==ProgramState.ARCHIVED;
      case ARCHIVED -> false;
    };
  }
  public static class ProgramUpdateDto {
  public String name;
  public String description;
  public String rulesJson;
}
}