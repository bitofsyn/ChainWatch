package com.chainwatch.backend.auth.api;

import com.chainwatch.backend.auth.exception.AuthenticationRequiredException;
import com.chainwatch.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request, Authentication authentication) {
        authService.logout(request.refreshToken(), principalName(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 인증 사용자 조회. jwt-enabled=false 모드에서는 시큐리티 체인이 익명을 걸러주지
     * 않으므로 컨트롤러에서 직접 확인해 401을 반환한다(프론트는 이를 "비로그인"으로 처리).
     */
    @GetMapping("/me")
    public UserSummary me(Authentication authentication) {
        return authService.me(requirePrincipalName(authentication));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest request, Authentication authentication) {
        authService.changePassword(requirePrincipalName(authentication), request);
        return ResponseEntity.noContent().build();
    }

    private static String requirePrincipalName(Authentication authentication) {
        String name = principalName(authentication);
        if (name == null) {
            throw new AuthenticationRequiredException();
        }
        return name;
    }

    private static String principalName(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
