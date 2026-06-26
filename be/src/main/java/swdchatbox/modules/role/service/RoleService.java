package swdchatbox.modules.role.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import swdchatbox.shared.dto.PageResponse;
import swdchatbox.shared.exception.BadRequestException;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.modules.role.dto.request.RoleFilterRequest;
import swdchatbox.modules.role.dto.request.RoleRequest;
import swdchatbox.modules.role.dto.request.RoleUpdateRequest;
import swdchatbox.modules.role.dto.response.RoleResponse;
import swdchatbox.modules.role.entity.Role;
import swdchatbox.modules.role.mapper.RoleMapper;
import swdchatbox.modules.role.repository.RoleRepository;
import swdchatbox.modules.role.repository.RoleSpecifications;
import swdchatbox.modules.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public PageResponse<RoleResponse> findAll(RoleFilterRequest filter, Pageable pageable) {
        Specification<Role> spec = Specification
                .where(RoleSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(RoleSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(RoleSpecifications.createdAfter(filter != null ? filter.getCreatedFrom() : null))
                .and(RoleSpecifications.createdBefore(filter != null ? filter.getCreatedTo() : null));

        Page<Role> page = roleRepository.findAll(spec, pageable);
        return PageResponse.<RoleResponse>builder()
                .content(page.getContent().stream().map(RoleMapper::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public List<RoleResponse> findAllActive() {
        return roleRepository.findAll().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(RoleMapper::toResponse)
                .toList();
    }

    public RoleResponse findById(UUID id) {
        return RoleMapper.toResponse(findRole(id));
    }

    public Role findRoleByCode(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + code));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Role code already exists");
        }

        Role role = Role.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName())
                .description(request.getDescription())
                .active(request.getActive() == null || request.getActive())
                .build();

        return RoleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, RoleUpdateRequest request) {
        Role role = findRole(id);

        if (request.getCode() != null) {
            if (request.getCode().isBlank()) {
                throw new BadRequestException("Role code cannot be blank");
            }
            String newCode = request.getCode().trim().toUpperCase();
            if (!role.getCode().equalsIgnoreCase(newCode) && roleRepository.existsByCode(newCode)) {
                throw new BadRequestException("Role code already exists");
            }
            role.setCode(newCode);
        }

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BadRequestException("Role name cannot be blank");
            }
            role.setName(request.getName());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            if (isSystemRole(role.getCode()) && !request.getActive()) {
                throw new BadRequestException("Cannot deactivate system role: " + role.getCode());
            }
            role.setActive(request.getActive());
        }

        return RoleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = findRole(id);

        if (isSystemRole(role.getCode())) {
            throw new BadRequestException("Cannot delete system role: " + role.getCode());
        }

        if (userRepository.existsByRole_Id(id)) {
            throw new BadRequestException("Cannot delete role that is assigned to users");
        }

        roleRepository.delete(role);
    }

    @Transactional
    public RoleResponse toggleActive(UUID id) {
        Role role = findRole(id);

        if (isSystemRole(role.getCode()) && Boolean.TRUE.equals(role.getActive())) {
            throw new BadRequestException("Cannot deactivate system role: " + role.getCode());
        }

        role.setActive(role.getActive() == null || !role.getActive());
        return RoleMapper.toResponse(roleRepository.save(role));
    }

    private Role findRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private boolean isSystemRole(String code) {
        return RoleCodes.ADMIN.equals(code)
                || RoleCodes.STUDENT.equals(code)
                || RoleCodes.LECTURER.equals(code);
    }
}
