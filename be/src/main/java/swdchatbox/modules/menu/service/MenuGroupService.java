package swdchatbox.modules.menu.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.menu.dto.request.CreateMenuGroupRequest;
import swdchatbox.modules.menu.dto.request.UpdateMenuGroupRequest;
import swdchatbox.modules.menu.dto.response.MenuGroupResponse;
import swdchatbox.modules.menu.entity.MenuGroup;
import swdchatbox.modules.menu.mapper.MenuMapper;
import swdchatbox.modules.menu.repository.MenuGroupRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuGroupService {

    private final MenuGroupRepository menuGroupRepository;

    public List<MenuGroupResponse> findAll() {
        return menuGroupRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(MenuMapper::toGroupResponse)
                .toList();
    }

    public List<MenuGroupResponse> findAllWithItems() {
        return menuGroupRepository.findAllWithItemsOrderByDisplayOrderAsc().stream()
                .map(MenuMapper::toGroupResponseWithItems)
                .toList();
    }

    @Transactional
    public List<MenuGroupResponse> findActiveForSidebar(String roleCode) {
        return menuGroupRepository.findAllWithItemsOrderByDisplayOrderAsc().stream()
                .filter(group -> Boolean.TRUE.equals(group.getActive()))
                .map(group -> MenuMapper.toActiveGroupResponse(group, roleCode))
                .filter(group -> group.getItems() != null && !group.getItems().isEmpty())
                .toList();
    }

    public MenuGroupResponse findById(UUID id) {
        return MenuMapper.toGroupResponseWithItems(findGroupWithItems(id));
    }

    @Transactional
    public MenuGroupResponse create(CreateMenuGroupRequest request) {
        String name = normalize(request.getName());
        if (menuGroupRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Menu group name already exists");
        }

        MenuGroup group = MenuGroup.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(request.getActive() == null || request.getActive())
                .build();

        return MenuMapper.toGroupResponse(menuGroupRepository.save(group));
    }

    @Transactional
    public MenuGroupResponse update(UUID id, UpdateMenuGroupRequest request) {
        MenuGroup group = findGroup(id);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BadRequestException("Menu group name cannot be blank");
            }
            String name = normalize(request.getName());
            if (menuGroupRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new BadRequestException("Menu group name already exists");
            }
            group.setName(name);
        }

        if (request.getDescription() != null) {
            group.setDescription(trimToNull(request.getDescription()));
        }

        if (request.getDisplayOrder() != null) {
            group.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getActive() != null) {
            group.setActive(request.getActive());
        }

        return MenuMapper.toGroupResponseWithItems(menuGroupRepository.save(group));
    }

    @Transactional
    public void delete(UUID id) {
        MenuGroup group = findGroup(id);
        menuGroupRepository.delete(group);
    }

    @Transactional
    public MenuGroupResponse toggleActive(UUID id) {
        MenuGroup group = findGroup(id);
        group.setActive(group.getActive() == null || !group.getActive());
        return MenuMapper.toGroupResponse(menuGroupRepository.save(group));
    }

    public MenuGroup findGroup(UUID id) {
        return menuGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu group not found"));
    }

    private MenuGroup findGroupWithItems(UUID id) {
        return menuGroupRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu group not found"));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
