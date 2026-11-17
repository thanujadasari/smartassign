package com.smartassign.auth;

import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.common.ApiException;
import com.smartassign.common.CurrentUser;
import com.smartassign.config.AppProperties;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.RecordSource;
import com.smartassign.handler.Handler;
import com.smartassign.handler.HandlerRepository;
import com.smartassign.location.CityDistanceService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    /** Default capacity for a newly registered handler; a supervisor adjusts it on the Handlers page. */
    static final int DEFAULT_HANDLER_MAX_WORKLOAD = 10;

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final HandlerRepository handlers;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;
    private final CityDistanceService cities;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    public AuthService(AuthenticationManager authenticationManager, UserRepository users, HandlerRepository handlers,
                       JwtService jwt, PasswordEncoder encoder, AuditService audit, AppProperties props,
                       CityDistanceService cities, org.springframework.transaction.support.TransactionTemplate tx) {
        this.tx = tx;
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.handlers = handlers;
        this.jwt = jwt;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
        this.cities = cities;
    }

    static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Not one transaction: a failed login's audit record must survive the authentication exception. */
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        String email = normaliseEmail(req.email());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (AuthenticationException e) {
            audit.record(AuditEntry.of("LOGIN_FAILED", "USER", null, email == null ? "unknown" : email));
            throw e;
        }
        User user = users.findByUsernameIgnoreCase(email).orElseThrow();
        user.setLastLoginAt(Instant.now());
        users.save(user);
        JwtService.IssuedToken token = jwt.issue(user);
        audit.record(AuditEntry.of("LOGIN", "USER", String.valueOf(user.getId()), user.getUsername())
                .details(user.getRole() + " signed in"));
        return new AuthDtos.LoginResponse(token.token(), "Bearer", token.expiresAt(), dto(user));
    }

    public void logout(String bearerToken, String username) {
        if (bearerToken != null) {
            jwt.revoke(bearerToken);
        }
        audit.record(AuditEntry.of("LOGOUT", "USER", null, username));
    }

    public AuthDtos.RegistrationOptions options() {
        AppProperties.Registration r = props.registration();
        return new AuthDtos.RegistrationOptions(r != null && r.staffSignupEnabled());
    }

    @Transactional
    public AuthDtos.UserDto registerCustomer(AuthDtos.CustomerSignupRequest req) {
        User u = newUser(req.fullName(), req.email(), req.phone(), req.password(), Role.CUSTOMER);
        audit.record(AuditEntry.of("USER_REGISTERED", "USER", String.valueOf(u.getId()), u.getUsername())
                .details("Customer account created"));
        return dto(u);
    }

    /** The code check runs outside the account transaction so a rejected attempt is still audited. */
    public AuthDtos.UserDto registerStaff(AuthDtos.StaffSignupRequest req) {
        AppProperties.Registration r = props.registration();
        if (r == null || !r.staffSignupEnabled()) {
            throw ApiException.forbidden("Staff sign-up is disabled on this server");
        }
        if (!constantTimeEquals(r.staffCode(), req.staffCode())) {
            audit.record(AuditEntry.of("STAFF_SIGNUP_REJECTED", "USER", null, normaliseEmail(req.email()))
                    .details("Invalid staff registration code"));
            throw ApiException.forbidden("The staff registration code is not valid");
        }
        return tx.execute(status -> createStaff(req));
    }

    private AuthDtos.UserDto createStaff(AuthDtos.StaffSignupRequest req) {
        Role role = req.role() == AuthDtos.StaffRole.SUPERVISOR ? Role.SUPERVISOR : Role.HANDLER;
        if (role == Role.HANDLER && req.handlerProfile() == null) {
            throw ApiException.badRequest("Handlers must provide expertise, experience and location");
        }
        if (role == Role.HANDLER && !cities.isKnownCity(req.handlerProfile().location())) {
            throw ApiException.badRequest("Location must be one of the supported cities");
        }
        User u = newUser(req.fullName(), req.email(), req.phone(), req.password(), role);
        Long handlerId = null;
        if (role == Role.HANDLER) {
            AuthDtos.HandlerProfile p = req.handlerProfile();
            Handler h = new Handler();
            h.setCode(String.format("H%03d", handlers.nextCodeNumber()));
            h.setUserId(u.getId());
            h.setFullName(u.getFullName());
            h.setEmail(u.getEmail());
            h.setPhone(u.getPhone());
            h.setPrimaryExpertise(p.primaryExpertise());
            Set<ClaimType> skills = EnumSet.noneOf(ClaimType.class);
            skills.addAll(p.primaryExpertise().impliedSkills());
            h.setSkills(skills);
            h.setExperienceYears(p.experienceYears());
            h.setLocation(cities.canonical(p.location()));
            h.setCurrentWorkload(0);
            h.setMaxWorkload(DEFAULT_HANDLER_MAX_WORKLOAD);
            h.setAvailable(true);
            h.setActive(true);
            h.setAvailabilityChangedAt(Instant.now());
            h.setSource(RecordSource.STAFF_REGISTRATION);
            handlers.save(h);
            handlerId = h.getId();
            audit.record(AuditEntry.of("HANDLER_CREATED", "HANDLER", h.getCode(), u.getUsername()).finalHandler(h.getCode())
                    .details("Handler registered: " + h.getPrimaryExpertise().label() + ", " + h.getLocation()
                            + ", " + h.getExperienceYears() + " years"));
        }
        audit.record(AuditEntry.of("USER_REGISTERED", "USER", String.valueOf(u.getId()), u.getUsername())
                .details(role + " account created"));
        return AuthDtos.UserDto.from(u, handlerId);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserDto me(String username) {
        return users.findByUsernameIgnoreCase(username).map(this::dto)
                .orElseThrow(() -> ApiException.notFound("User", username));
    }

    /** The signed-in user entity (for services that record who did what). */
    @Transactional(readOnly = true)
    public User currentUser() {
        return users.findByUsernameIgnoreCase(CurrentUser.username())
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please sign in again."));
    }

    private User newUser(String fullName, String email, String phone, String password, Role role) {
        String e = normaliseEmail(email);
        if (users.existsByEmailIgnoreCase(e) || users.existsByUsernameIgnoreCase(e)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User u = new User();
        u.setUsername(e);                 // the email is the login name
        u.setEmail(e);
        u.setFullName(fullName.trim());
        u.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    private AuthDtos.UserDto dto(User u) {
        Long handlerId = u.getRole() == Role.HANDLER
                ? handlers.findByUserId(u.getId()).map(Handler::getId).orElse(null) : null;
        return AuthDtos.UserDto.from(u, handlerId);
    }

    static boolean constantTimeEquals(String expected, String given) {
        if (expected == null || given == null) return false;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] a = md.digest(expected.getBytes(StandardCharsets.UTF_8));
            byte[] b = MessageDigest.getInstance("SHA-256").digest(given.trim().getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
