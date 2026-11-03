package edu.campus.recycle;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationTest {
    private JdbcTemplate db;
    private MockMvc mvc;
    private String hash;
    @BeforeEach void setup() {
        db = mock(JdbcTemplate.class);
        hash = PasswordHasher.hash("user-demo-hash");
        when(db.queryForList("SELECT id,password_hash FROM users WHERE username=? AND status='ACTIVE'", "student01"))
            .thenReturn(List.of(Map.of("id",2L,"password_hash",hash)));
        when(db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'",2L)).thenReturn(List.of(Map.of("role","USER")));
        when(db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'",1L)).thenReturn(List.of(Map.of("role","ADMIN")));
        when(db.queryForMap("SELECT id,username,real_name,phone,role,total_points,reputation_score FROM users WHERE id=? AND status='ACTIVE'",2L))
            .thenReturn(Map.of("id",2L,"username","student01","role","USER"));
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(db),new ApiController(db),new PhotoController())
            .addInterceptors(new AuthInterceptor(db)).setControllerAdvice(new ApiErrors()).build();
    }
    private MockHttpSession session(long id) {
        MockHttpSession session = new MockHttpSession(); session.setAttribute("userId",id);session.setAttribute("csrfToken","test-csrf");return session;
    }
    @Test void passwordIsSaltedAndVerifiable() {
        assertTrue(hash.length()<=100); assertNotEquals(hash,PasswordHasher.hash("user-demo-hash"));
        assertTrue(PasswordHasher.matches("user-demo-hash",hash)); assertFalse(PasswordHasher.matches("wrong",hash));
        assertFalse(PasswordHasher.matches("user-demo-hash","user-demo-hash"));
    }
    @Test void migratesLegacyPasswordsOnlyOnce() {
        when(db.queryForList("SELECT id,password_hash FROM users")).thenReturn(List.of(Map.of("id",2L,"password_hash","legacy-secret"),Map.of("id",1L,"password_hash",hash)));
        new AuthController(db).migratePasswords();
        verify(db).update(eq("UPDATE users SET password_hash=? WHERE id=? AND password_hash=?"),argThat((Object value)->PasswordHasher.matches("legacy-secret",String.valueOf(value))),eq(2L),eq("legacy-secret"));
        verify(db, times(1)).update(anyString(),any(Object[].class));
    }
    @Test void rejectsAnonymousReadsAndUploads() throws Exception {
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/photos").file("file",new byte[]{1})).andExpect(status().isUnauthorized());
    }
    @Test void loginRotatesSessionAndReturnsNoPassword() throws Exception {
        MockHttpSession old = session(1L);
        MvcResult result=mvc.perform(post("/api/auth/login").session(old).header("X-Requested-With","XMLHttpRequest")
            .contentType("application/json").content("{\"username\":\"student01\",\"password\":\"user-demo-hash\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(2)).andExpect(jsonPath("$.csrfToken").isString())
            .andExpect(jsonPath("$.user.password_hash").doesNotExist()).andReturn();
        assertTrue(old.isInvalid()); assertNotEquals(old.getId(),result.getRequest().getSession().getId());
    }
    @Test void wrongPasswordAndCrossSiteLoginAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Requested-With","XMLHttpRequest").contentType("application/json")
            .content("{\"username\":\"student01\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void userCannotImpersonateAdminOrOtherOwner() throws Exception {
        mvc.perform(get("/api/admin/users?operatorId=1").session(session(2L))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trades?userId=1&admin=true").session(session(2L))).andExpect(status().isForbidden());
        mvc.perform(get("/api/points?userId=3").session(session(2L))).andExpect(status().isForbidden());
        mvc.perform(get("/api/items?ownerId=3&ownerOnly=true").session(session(2L))).andExpect(status().isForbidden());
        mvc.perform(put("/api/items/10").session(session(2L)).header("X-CSRF-Token","test-csrf")
            .contentType("application/json").content("{\"ownerId\":3}")).andExpect(status().isForbidden());
    }
    @Test void unsafeRequestsRequireCsrfAndLogoutInvalidatesSession() throws Exception {
        MockHttpSession session=session(2L);
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-Token","wrong")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-Token","test-csrf")).andExpect(status().isOk());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void disabledUserSessionIsInvalidated() throws Exception {
        when(db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'",2L)).thenReturn(List.of());
        MockHttpSession session=session(2L);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());assertTrue(session.isInvalid());
    }
    @Test void actualSessionAdminCanQueryUsers() throws Exception {
        when(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'",Integer.class,1L)).thenReturn(1);
        mvc.perform(get("/api/admin/users?operatorId=1").session(session(1L))).andExpect(status().isOk());
    }
}
