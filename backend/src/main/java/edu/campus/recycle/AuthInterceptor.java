package edu.campus.recycle;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JdbcTemplate db;
    public AuthInterceptor(JdbcTemplate db) { this.db = db; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Photos contain no identity data and must load in ordinary <img> requests.
        if ("POST".equals(request.getMethod()) && Set.of("/api/auth/login", "/api/auth/register").contains(path)) {
            // A custom header cannot be sent by a cross-site HTML form. No cross-origin CORS is enabled.
            if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请通过本站页面登录或注册");
            response.setHeader("Cache-Control", "no-store");
            return true;
        }
        if ("GET".equals(request.getMethod()) && path.startsWith("/api/photos/")) return true;
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Number uid))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        List<Map<String,Object>> users = db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'", uid.longValue());
        if (users.isEmpty()) { session.invalidate(); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用，请重新登录"); }
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String expected = Objects.toString(session.getAttribute("csrfToken"), "");
            String supplied = Objects.toString(request.getHeader("X-CSRF-Token"), "");
            if (expected.isEmpty() || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "会话校验失败，请刷新页面后重试");
        }
        request.setAttribute("authenticatedUserId", uid.longValue());
        request.setAttribute("authenticatedRole", users.get(0).get("role"));
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(users.get(0).get("role")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        response.setHeader("Cache-Control", "no-store");
        return true;
    }
}
