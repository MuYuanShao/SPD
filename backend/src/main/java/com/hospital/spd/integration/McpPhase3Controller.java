package com.hospital.spd.integration;

import com.hospital.spd.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Exposes administrative backfill/retry and the dedicated supplier-feedback receiver. */
@RestController
public class McpPhase3Controller {
    private final McpPhase3Outbox outbox;private final McpPhase3FeedbackService feedback;
    public McpPhase3Controller(McpPhase3Outbox outbox,McpPhase3FeedbackService feedback){this.outbox=outbox;this.feedback=feedback;}
    @GetMapping("/mcp-phase3/status") public Object status(){return ApiResponse.ok(outbox.status());}
    @GetMapping("/mcp-phase3/feedback") public Object feedback(){return ApiResponse.ok(Map.of("rows",feedback.list()));}
    @PostMapping("/mcp-phase3/backfill") public Object backfill(@RequestParam(defaultValue="100") int size,
            @RequestParam(required=false) String supplierCode,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate){return ApiResponse.ok(outbox.backfill(size,supplierCode,startDate,endDate));}
    @PostMapping("/mcp-phase3/retry/{id}") public Object retry(@PathVariable String id){outbox.retry(id);return ApiResponse.ok(Map.of("messageId",id));}
    @PostMapping("/mcp-phase3/republish/{kind}/{source}") public Object republish(@PathVariable String kind,@PathVariable long source){outbox.republish(kind,source);return ApiResponse.ok(Map.of("sourceId",Long.toString(source)));}
    @PostMapping("/mcp/phase3/feedback") public Object receive(HttpServletRequest request,@RequestHeader("Idempotency-Key") String key) throws Exception {
        byte[] body=request.getInputStream().readNBytes(64*1024+1);
        if(body.length>64*1024)throw new IllegalArgumentException("反馈内容超过64KB");
        return feedback.receive(key,Phase3Json.MAPPER.readTree(body));
    }
}
