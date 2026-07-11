package swdchatbox.system.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.system.menu.dto.response.MenuGroupResponse;
import swdchatbox.system.menu.service.MenuGroupService;

import java.util.List;

@RestController
@RequestMapping("/sidebar")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SidebarController {

    private final MenuGroupService menuGroupService;

    @Operation(
            summary = "Lấy sidebar theo role hiện tại",
            description = "Trả về menu group/item đang active. Item có requiredRole sẽ chỉ hiện với đúng role đó."
    )
    @GetMapping
    public ResponseEntity<List<MenuGroupResponse>> getSidebar(Authentication authentication) {
        String roleCode = resolveRoleCode(authentication);
        return ResponseEntity.ok(menuGroupService.findActiveForSidebar(roleCode));
    }

    private String resolveRoleCode(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }
}
