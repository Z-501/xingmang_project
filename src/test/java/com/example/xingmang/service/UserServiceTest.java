package com.example.xingmang.service;

import com.example.xingmang.model.entity.User;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Integration test requires MySQL and Redis test containers or local services.")
@ActiveProfiles("test")
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("register, login, refresh and logout should complete successfully")
    void shouldCompleteFullAuthenticationFlow() {
        String phone = "13812345678";
        String password = "testPassword123";

        User user = new User();
        user.setPhone(phone);
        user.setPassword(password);

        try {
            userService.register(user);
        } catch (RuntimeException ignored) {
            // The account may already exist when the integration test is executed repeatedly.
        }

        Map<String, Object> loginResult = userService.login(phone, password);
        assertNotNull(loginResult.get("accessToken"));
        assertNotNull(loginResult.get("refreshToken"));

        Map<String, Object> refreshResult = userService.refresh((String) loginResult.get("refreshToken"));
        assertNotNull(refreshResult.get("accessToken"));
        assertNotNull(refreshResult.get("refreshToken"));

        userService.logout(user.getId());
    }
}
