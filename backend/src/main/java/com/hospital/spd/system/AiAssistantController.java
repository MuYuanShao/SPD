package com.hospital.spd.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.spd.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

/** Exposes authenticated model discovery and assistant conversation endpoints. */
@RestController
@RequestMapping("/ai-assistant")
public class AiAssistantController {
    private final AiAssistantService service;

    public AiAssistantController(AiAssistantService service) {
        this.service = service;
    }

    @GetMapping("/models")
    public ApiResponse<JsonNode> models() {
        return ApiResponse.ok(service.models());
    }

    @PostMapping("/chat")
    public ApiResponse<AiAssistantService.ChatReply> chat(@RequestBody AiAssistantService.ChatRequest request) {
        return ApiResponse.ok(service.chat(request));
    }
}
