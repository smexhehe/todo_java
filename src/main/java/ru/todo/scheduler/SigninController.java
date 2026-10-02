package ru.todo.scheduler;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SigninController {
    private final TokenService tokens;

    public SigninController(TokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/api/signin")
    public Map<String, String> signin(@RequestBody PasswordRequest request) {
        if (!tokens.passwordMatches(request.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid password");
        }
        return Map.of("token", tokens.issue());
    }

    public record PasswordRequest(String password) {
    }
}
