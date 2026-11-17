package com.smartassign.auth;

import com.smartassign.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        auth.logout(token, CurrentUser.username());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.UserDto me() {
        return auth.me(CurrentUser.username());
    }

    @GetMapping("/registration-options")
    public AuthDtos.RegistrationOptions options() {
        return auth.options();
    }

    @PostMapping("/register/customer")
    public ResponseEntity<AuthDtos.UserDto> registerCustomer(@Valid @RequestBody AuthDtos.CustomerSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.registerCustomer(req));
    }

    @PostMapping("/register/staff")
    public ResponseEntity<AuthDtos.UserDto> registerStaff(@Valid @RequestBody AuthDtos.StaffSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.registerStaff(req));
    }
}
