package com.smartassign.auth;

import com.smartassign.domain.Expertise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {
    }

    public record LoginResponse(String token, String tokenType, Instant expiresAt, UserDto user) {
    }

    /** Public sign-up for customers. */
    public record CustomerSignupRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email address is not valid") @Size(max = 160) String email,
            @Pattern(regexp = "^[0-9+()\\- ]{7,30}$", message = "Phone number is not valid") String phone,
            @NotBlank @Size(min = 10, max = 100, message = "Password must be 10-100 characters") String password) {
    }

    public enum StaffRole { SUPERVISOR, HANDLER }

    /** Handler profile entered at sign-up. Capacity is set by a supervisor afterwards. */
    public record HandlerProfile(
            @NotNull(message = "Primary expertise is required") Expertise primaryExpertise,
            @NotNull @Min(value = 0, message = "Experience cannot be negative") @Max(value = 60, message = "Experience must be 60 years or less") Integer experienceYears,
            @NotBlank(message = "Location is required") @Size(max = 60) String location) {
    }

    /** Sign-up for supervisors and handlers; requires the staff registration code from the server configuration. */
    public record StaffSignupRequest(
            @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Email address is not valid") @Size(max = 160) String email,
            @Pattern(regexp = "^[0-9+()\\- ]{7,30}$", message = "Phone number is not valid") String phone,
            @NotBlank @Size(min = 10, max = 100, message = "Password must be 10-100 characters") String password,
            @NotNull(message = "Role is required") StaffRole role,
            @NotBlank(message = "Staff registration code is required") String staffCode,
            @Valid HandlerProfile handlerProfile) {
    }

    public record RegistrationOptions(boolean staffSignupEnabled) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, Role role, Long handlerId,
                          Instant lastLoginAt) {
        public static UserDto from(User u, Long handlerId) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), handlerId,
                    u.getLastLoginAt());
        }
    }
}
