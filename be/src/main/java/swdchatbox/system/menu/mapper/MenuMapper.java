package swdchatbox.system.menu.mapper;

import swdchatbox.system.menu.dto.response.MenuGroupResponse;
import swdchatbox.system.menu.dto.response.MenuItemResponse;
import swdchatbox.system.menu.entity.MenuGroup;
import swdchatbox.system.menu.entity.MenuItem;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MenuMapper {

    private MenuMapper() {
    }

    public static MenuGroupResponse toGroupResponse(MenuGroup group) {
        return toGroupResponse(group, false);
    }

    public static MenuGroupResponse toGroupResponseWithItems(MenuGroup group) {
        return toGroupResponse(group, true);
    }

    public static MenuGroupResponse toActiveGroupResponse(MenuGroup group, String roleCode) {
        if (group == null) {
            return null;
        }

        List<MenuItemResponse> items = group.getItems() == null
                ? Collections.emptyList()
                : group.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(item -> isVisibleForRole(item.getRequiredRole(), roleCode))
                .sorted(Comparator.comparing(MenuItem::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(MenuMapper::toItemResponse)
                .toList();

        return MenuGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .active(group.getActive())
                .displayOrder(group.getDisplayOrder())
                .items(items)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    public static MenuItemResponse toItemResponse(MenuItem item) {
        if (item == null) {
            return null;
        }
        return MenuItemResponse.builder()
                .id(item.getId())
                .menuGroupId(item.getMenuGroup() != null ? item.getMenuGroup().getId() : null)
                .title(item.getTitle())
                .url(item.getUrl())
                .icon(item.getIcon())
                .description(item.getDescription())
                .displayOrder(item.getDisplayOrder())
                .active(item.getActive())
                .requiredRole(item.getRequiredRole())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private static MenuGroupResponse toGroupResponse(MenuGroup group, boolean includeItems) {
        if (group == null) {
            return null;
        }

        List<MenuItemResponse> items = null;
        if (includeItems) {
            items = group.getItems() == null
                    ? Collections.emptyList()
                    : group.getItems().stream()
                    .sorted(Comparator.comparing(MenuItem::getDisplayOrder, Comparator.nullsLast(Integer::compareTo)))
                    .map(MenuMapper::toItemResponse)
                    .toList();
        }

        return MenuGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .active(group.getActive())
                .displayOrder(group.getDisplayOrder())
                .items(items)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private static boolean isVisibleForRole(String requiredRole, String roleCode) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return true;
        }
        return roleCode != null && requiredRole.equalsIgnoreCase(roleCode);
    }
}
