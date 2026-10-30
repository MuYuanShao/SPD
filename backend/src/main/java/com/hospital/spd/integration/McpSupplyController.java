package com.hospital.spd.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Exposes the dedicated authenticated supplier-delivery receiver and hospital receipt status. */
@RestController
@RequestMapping("/mcp/phase3")
public class McpSupplyController {
    private final McpSupplyReceiver service;
    public McpSupplyController(McpSupplyReceiver service){this.service=service;}
    @GetMapping("/supply-options") public Object options(@RequestParam String organization,@RequestParam String orderNo,@RequestParam String supplierCode){return service.options(organization,orderNo,supplierCode);}
    @PostMapping("/supplies") public Object receive(@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws Exception {
        byte[] bytes=request.getInputStream().readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new IllegalArgumentException("供货消息超过2MB");
        com.fasterxml.jackson.databind.JsonNode json;
        try{json=Phase3Json.MAPPER.readTree(bytes);}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("供货JSON格式不合法");}
        return service.receive(key,json);
    }
    @GetMapping("/supplies/{key}") public Object receipt(@PathVariable String key,@RequestParam String organization,@RequestParam String supplierCode){return service.receipt(organization,key,supplierCode);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error){return ResponseEntity.badRequest().body(Map.of("state","failed","message",error.getMessage()));}
    @ExceptionHandler(McpSupplyReceiver.Conflict.class) public ResponseEntity<?> conflict(McpSupplyReceiver.Conflict error){return ResponseEntity.status(409).body(Map.of("state","failed","message",error.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> disabled(IllegalStateException error){return ResponseEntity.status(503).body(Map.of("state","failed","message",error.getMessage()));}
}
