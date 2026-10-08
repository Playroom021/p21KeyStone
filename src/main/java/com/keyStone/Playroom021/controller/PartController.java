package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.PartRequest;
import com.keyStone.Playroom021.dto.PartResponse;
import com.keyStone.Playroom021.service.PartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Parts inventory CRUD (Step 6). Role gating is in SecurityConfig. */
@RestController
@RequestMapping("/api/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService partService;

    @PostMapping
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partService.create(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PartResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(partService.search(search, lowStock, page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(partService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PartResponse> update(@PathVariable Long id, @Valid @RequestBody PartRequest request) {
        return ResponseEntity.ok(partService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        partService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
