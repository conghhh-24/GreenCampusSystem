package edu.campus.recycle;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

abstract class TestSessionSupport {
    @BeforeEach void authenticatedUser() { signedIn(2L); }
    void signedIn(long id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("authenticatedUserId", id);
        request.setAttribute("authenticatedRole", id == 1L ? "ADMIN" : "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }
}
