package edu.campus.recycle;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** Identity is set only by AuthInterceptor after checking the server session. */
public final class CurrentUser {
    private CurrentUser() {}
    private static HttpServletRequest request() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return attributes.getRequest();
    }
    public static long id() {
        Object value = request().getAttribute("authenticatedUserId");
        if (!(value instanceof Number number)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        return number.longValue();
    }
    public static boolean admin() { return "ADMIN".equals(request().getAttribute("authenticatedRole")); }
    public static long actor(Object claimedId) {
        long actual = id();
        try {
            if (claimedId != null && Long.parseLong(String.valueOf(claimedId)) != actual)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能冒用其他用户身份");
        } catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户编号不合法"); }
        return actual;
    }
    public static void selfOrAdmin(long target) {
        if (id() != target && !admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看其他用户的数据");
    }
}
