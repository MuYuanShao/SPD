package com.hospital.spd.system;

import com.hospital.spd.common.ApiResponse;
import com.hospital.spd.system.service.SystemNoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Exposes notice publishing and the signed-in user's unread notices. */
@RestController
@RequestMapping("/system-notices")
public class SystemNoticeController {
    private final SystemNoticeService notices;

    public SystemNoticeController(SystemNoticeService notices) {
        this.notices = notices;
    }

    @PostMapping
    public ApiResponse<Void> publish(@RequestBody PublishRequest request) {
        notices.publish(request.title(), request.content());
        return ApiResponse.ok(null);
    }

    @PostMapping("/login-delivery")
    public ApiResponse<SystemNoticeService.Notice> claimForLogin(@RequestParam UUID loginId) {
        return ApiResponse.ok(notices.claimForLogin(loginId));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> acknowledge(@PathVariable long id) {
        notices.acknowledge(id);
        return ApiResponse.ok(null);
    }

    public record PublishRequest(String title, String content) {}
}
