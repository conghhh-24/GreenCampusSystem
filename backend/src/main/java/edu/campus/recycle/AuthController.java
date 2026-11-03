package edu.campus.recycle;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate db;
    public AuthController(JdbcTemplate db) { this.db = db; }
    @PostConstruct public void migratePasswords() {
        for (Map<String,Object> row : db.queryForList("SELECT id,password_hash FROM users")) {
            String old = Objects.toString(row.get("password_hash"), "");
            if (!PasswordHasher.encoded(old))
                db.update("UPDATE users SET password_hash=? WHERE id=? AND password_hash=?", PasswordHasher.hash(old), row.get("id"), old);
        }
    }
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,Object> body, HttpServletRequest request) {
        String username = Objects.toString(body.get("username"), "");
        String password = Objects.toString(body.get("password"), "");
        if (username.length() > 32 || password.length() > 32) throw invalidLogin();
        List<Map<String,Object>> rows = db.queryForList("SELECT id,password_hash FROM users WHERE username=? AND status='ACTIVE'", username);
        if (rows.isEmpty() || !PasswordHasher.matches(password, Objects.toString(rows.get(0).get("password_hash"), ""))) throw invalidLogin();
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", ((Number)rows.get(0).get("id")).longValue());
        session.setAttribute("csrfToken", UUID.randomUUID().toString());
        return profile(session);
    }
    @GetMapping("/me") public Map<String,Object> me(HttpServletRequest request) { return profile(request.getSession(false)); }
    @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("message", "已退出登录");
    }
    private Map<String,Object> profile(HttpSession session) {
        Map<String,Object> user = new HashMap<>(db.queryForMap("SELECT id,username,real_name,phone,role,total_points,reputation_score FROM users WHERE id=? AND status='ACTIVE'", session.getAttribute("userId")));
        return Map.of("user", user, "csrfToken", session.getAttribute("csrfToken"));
    }
    private ResponseStatusException invalidLogin() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误"); }
}
