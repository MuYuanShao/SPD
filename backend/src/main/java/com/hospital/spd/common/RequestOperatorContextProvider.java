package com.hospital.spd.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
public class RequestOperatorContextProvider implements OperatorContextProvider {

    @Override
    public OperatorContext current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Long userId = attributeAsLong(request, "currentUserId");
            String username = attributeAsString(request, "currentUsername");
            Long deptId = attributeAsLong(request, "currentDeptId");
            Integer dataScope = attributeAsInteger(request, "currentDataScope");
            if (userId != null && username != null && !username.isBlank()) {
                return new OperatorContext(userId, username, clientIp(request), roles(request), deptId, dataScope);
            }
        }
        return OperatorContext.system();
    }

    private static Long attributeAsLong(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer attributeAsInteger(HttpServletRequest request, String name) {
        Long value = attributeAsLong(request, name);
        return value == null ? null : value.intValue();
    }

    private static String attributeAsString(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> roles(HttpServletRequest request) {
        Object value = request.getAttribute("currentRoles");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of("ROLE_USER");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
