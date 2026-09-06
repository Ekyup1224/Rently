package mn.innex.stay.user.web;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.user.service.UserService;
import mn.innex.stay.user.web.dto.HostApplicationRequest;
import mn.innex.stay.user.web.dto.HostApplicationResponse;
import mn.innex.stay.user.web.dto.SetPasswordRequest;
import mn.innex.stay.user.web.dto.UpdateProfileRequest;
import mn.innex.stay.user.web.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account. There is no {@code /users/{id}} on this
 * controller by design: reaching another account goes through the admin API,
 * which requires SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse profile() {
        return UserResponse.from(userService.require(CurrentActor.requireUserId()));
    }

    @PatchMapping
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                      HttpServletRequest httpRequest) {
        return UserResponse.from(userService.updateProfile(
                CurrentActor.requireUserId(), request, ClientIp.of(httpRequest)));
    }

    /** Sets or changes the password. OTP-only accounts use this to gain one. */
    @PostMapping("/password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        userService.setPassword(CurrentActor.requireUserId(), request, ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** Applies to become a HOUSE_OWNER or HOTEL_MANAGER. Reviewed by an admin. */
    @PostMapping("/host-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public HostApplicationResponse apply(@Valid @RequestBody HostApplicationRequest request,
                                         HttpServletRequest httpRequest) {
        return HostApplicationResponse.from(userService.submitHostApplication(
                CurrentActor.requireUserId(), request, ClientIp.of(httpRequest)));
    }

    @GetMapping("/host-applications")
    public List<HostApplicationResponse> myApplications() {
        return userService.listHostApplications(CurrentActor.requireUserId()).stream()
                .map(HostApplicationResponse::from)
                .toList();
    }

    @PostMapping("/host-applications/{applicationId}/withdraw")
    public HostApplicationResponse withdraw(@PathVariable UUID applicationId,
                                            HttpServletRequest httpRequest) {
        return HostApplicationResponse.from(userService.withdrawHostApplication(
                CurrentActor.requireUserId(), applicationId, ClientIp.of(httpRequest)));
    }
}
