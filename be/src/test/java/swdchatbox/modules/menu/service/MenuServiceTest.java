package swdchatbox.modules.menu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.modules.menu.dto.request.CreateMenuGroupRequest;
import swdchatbox.modules.menu.dto.response.MenuGroupResponse;
import swdchatbox.modules.menu.entity.MenuGroup;
import swdchatbox.modules.menu.entity.MenuItem;
import swdchatbox.modules.menu.repository.MenuGroupRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuGroupRepository menuGroupRepository;

    @InjectMocks
    private MenuGroupService menuGroupService;

    @Test
    void createRejectsDuplicateName() {
        CreateMenuGroupRequest request = new CreateMenuGroupRequest();
        request.setName("Main");

        when(menuGroupRepository.existsByNameIgnoreCase("Main")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> menuGroupService.create(request));
    }

    @Test
    void sidebarFiltersByRoleAndActive() {
        MenuGroup group = MenuGroup.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .active(true)
                .displayOrder(1)
                .build();

        MenuItem adminItem = MenuItem.builder()
                .id(UUID.randomUUID())
                .menuGroup(group)
                .title("Users")
                .url("/admin/users")
                .active(true)
                .displayOrder(1)
                .requiredRole(RoleCodes.ADMIN)
                .build();

        MenuItem commonItem = MenuItem.builder()
                .id(UUID.randomUUID())
                .menuGroup(group)
                .title("Chat")
                .url("/chat")
                .active(true)
                .displayOrder(2)
                .requiredRole(null)
                .build();

        MenuItem inactiveItem = MenuItem.builder()
                .id(UUID.randomUUID())
                .menuGroup(group)
                .title("Hidden")
                .url("/hidden")
                .active(false)
                .displayOrder(3)
                .build();

        group.getItems().addAll(List.of(adminItem, commonItem, inactiveItem));

        when(menuGroupRepository.findAllWithItemsOrderByDisplayOrderAsc()).thenReturn(List.of(group));

        List<MenuGroupResponse> studentSidebar = menuGroupService.findActiveForSidebar(RoleCodes.STUDENT);
        assertEquals(1, studentSidebar.size());
        assertEquals(1, studentSidebar.get(0).getItems().size());
        assertEquals("Chat", studentSidebar.get(0).getItems().get(0).getTitle());

        List<MenuGroupResponse> adminSidebar = menuGroupService.findActiveForSidebar(RoleCodes.ADMIN);
        assertEquals(1, adminSidebar.size());
        assertEquals(2, adminSidebar.get(0).getItems().size());
    }

    @Test
    void createSavesNormalizedName() {
        CreateMenuGroupRequest request = new CreateMenuGroupRequest();
        request.setName("  Main   Menu  ");
        request.setDisplayOrder(1);

        when(menuGroupRepository.existsByNameIgnoreCase("Main Menu")).thenReturn(false);
        when(menuGroupRepository.save(any(MenuGroup.class))).thenAnswer(invocation -> {
            MenuGroup group = invocation.getArgument(0);
            group.setId(UUID.randomUUID());
            return group;
        });

        MenuGroupResponse response = menuGroupService.create(request);

        assertEquals("Main Menu", response.getName());
        assertEquals(1, response.getDisplayOrder());
        assertTrue(response.getActive());
    }
}
