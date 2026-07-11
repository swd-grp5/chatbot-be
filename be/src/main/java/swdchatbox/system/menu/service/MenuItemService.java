package swdchatbox.system.menu.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.system.menu.dto.request.CreateMenuItemRequest;
import swdchatbox.system.menu.dto.request.UpdateMenuItemRequest;
import swdchatbox.system.menu.dto.response.MenuItemResponse;
import swdchatbox.system.menu.entity.MenuGroup;
import swdchatbox.system.menu.entity.MenuItem;
import swdchatbox.system.menu.mapper.MenuMapper;
import swdchatbox.system.menu.repository.MenuItemRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            RoleCodes.ADMIN,
            RoleCodes.STUDENT,
            RoleCodes.LECTURER
    );

    private final MenuItemRepository menuItemRepository;
    private final MenuGroupService menuGroupService;

    public List<MenuItemResponse> findByGroupId(UUID menuGroupId) {
        menuGroupService.findGroup(menuGroupId);
        return menuItemRepository.findByMenuGroup_IdOrderByDisplayOrderAsc(menuGroupId).stream()
                .map(MenuMapper::toItemResponse)
                .toList();
    }

    public MenuItemResponse findById(UUID id) {
        return MenuMapper.toItemResponse(findItem(id));
    }

    @Transactional
    public MenuItemResponse create(CreateMenuItemRequest request) {
        MenuGroup group = menuGroupService.findGroup(request.getMenuGroupId());
        String title = normalize(request.getTitle());
        String url = normalizeUrl(request.getUrl());

        validateTitleUnique(group.getId(), title, null);
        validateUrlUnique(url, null);
        String requiredRole = normalizeRequiredRole(request.getRequiredRole());

        MenuItem item = MenuItem.builder()
                .menuGroup(group)
                .title(title)
                .url(url)
                .icon(trimToNull(request.getIcon()))
                .description(trimToNull(request.getDescription()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(request.getActive() == null || request.getActive())
                .requiredRole(requiredRole)
                .build();

        return MenuMapper.toItemResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse update(UUID id, UpdateMenuItemRequest request) {
        MenuItem item = findItem(id);

        if (request.getMenuGroupId() != null
                && (item.getMenuGroup() == null || !request.getMenuGroupId().equals(item.getMenuGroup().getId()))) {
            MenuGroup newGroup = menuGroupService.findGroup(request.getMenuGroupId());
            item.setMenuGroup(newGroup);
        }

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BadRequestException("Menu item title cannot be blank");
            }
            String title = normalize(request.getTitle());
            validateTitleUnique(item.getMenuGroup().getId(), title, item.getId());
            item.setTitle(title);
        }

        if (request.getUrl() != null) {
            String url = normalizeUrl(request.getUrl());
            validateUrlUnique(url, item.getId());
            item.setUrl(url);
        }

        if (request.getIcon() != null) {
            item.setIcon(trimToNull(request.getIcon()));
        }

        if (request.getDescription() != null) {
            item.setDescription(trimToNull(request.getDescription()));
        }

        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }

        if (request.getRequiredRole() != null) {
            item.setRequiredRole(normalizeRequiredRole(request.getRequiredRole()));
        }

        return MenuMapper.toItemResponse(menuItemRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        MenuItem item = findItem(id);
        menuItemRepository.delete(item);
    }

    @Transactional
    public MenuItemResponse toggleActive(UUID id) {
        MenuItem item = findItem(id);
        item.setActive(item.getActive() == null || !item.getActive());
        return MenuMapper.toItemResponse(menuItemRepository.save(item));
    }

    private MenuItem findItem(UUID id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
    }

    private void validateTitleUnique(UUID groupId, String title, UUID excludeId) {
        boolean exists = excludeId == null
                ? menuItemRepository.existsByMenuGroup_IdAndTitleIgnoreCase(groupId, title)
                : menuItemRepository.existsByMenuGroup_IdAndTitleIgnoreCaseAndIdNot(groupId, title, excludeId);
        if (exists) {
            throw new BadRequestException("Menu item title already exists in this group");
        }
    }

    private void validateUrlUnique(String url, UUID excludeId) {
        if (url == null) {
            return;
        }
        boolean exists = excludeId == null
                ? menuItemRepository.existsByUrlIgnoreCase(url)
                : menuItemRepository.existsByUrlIgnoreCaseAndIdNot(url, excludeId);
        if (exists) {
            throw new BadRequestException("Menu item URL already exists");
        }
    }

    private static String normalizeRequiredRole(String requiredRole) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return null;
        }
        String role = requiredRole.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BadRequestException("Invalid requiredRole. Allowed: ADMIN, STUDENT, LECTURER");
        }
        return role;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.contains(" ")) {
            throw new BadRequestException("Menu item URL cannot contain spaces");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
