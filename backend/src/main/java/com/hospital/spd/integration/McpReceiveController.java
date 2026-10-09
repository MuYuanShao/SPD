package com.hospital.spd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Receives bounded MCP product envelopes and returns the dedicated integration receipt format. */
@RestController
@RequestMapping("/mcp")
public class McpReceiveController {
  private final McpReceiveService service;
  private final ObjectMapper mapper;
  public McpReceiveController(McpReceiveService service,ObjectMapper mapper) {this.service=service;this.mapper=mapper;}
  @PostMapping("/receive")
  public ResponseEntity<Map<String,Object>> receive(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key) throws Exception {
    byte[] body=request.getInputStream().readNBytes(25*1024*1024+1);
    if(body.length>25*1024*1024) return ResponseEntity.status(413).body(Map.of("state","failed","message","下发内容超过25MB"));
    try {return ResponseEntity.ok(service.receive(key,mapper.readTree(body)));}
    catch(IllegalArgumentException e) {return ResponseEntity.ok(Map.of("state","failed","message",e.getMessage()==null?"下发数据不合法":e.getMessage().substring(0,Math.min(1000,e.getMessage().length()))));}
  }
}
