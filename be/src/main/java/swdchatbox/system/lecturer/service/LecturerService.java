package swdchatbox.system.lecturer.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.document.repository.DocumentRepository;
import swdchatbox.system.lecturer.dto.request.LecturerFilterRequest;
import swdchatbox.system.lecturer.dto.request.LecturerRequest;
import swdchatbox.system.lecturer.dto.request.LecturerUpdateRequest;
import swdchatbox.system.lecturer.dto.response.LecturerResponse;
import swdchatbox.system.lecturer.mapper.LecturerMapper;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.entity.Role;
import swdchatbox.system.role.service.RoleService;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.AuthProvider;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.user.repository.UserSpecifications;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LecturerService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final DocumentRepository documentRepository;

    public PageResponse<LecturerResponse> findAll(LecturerFilterRequest filter, Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecifications.hasRoleCode(RoleCodes.LECTURER))
                .and(UserSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(UserSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(UserSpecifications.createdAfter(filter != null ? filter.getCreatedFrom() : null))
                .and(UserSpecifications.createdBefore(filter != null ? filter.getCreatedTo() : null));

        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.<LecturerResponse>builder()
                .content(page.getContent().stream().map(LecturerMapper::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public LecturerResponse findById(UUID id) {
        return LecturerMapper.toResponse(findLecturer(id));
    }

    @Transactional
    public LecturerResponse create(LecturerRequest request) {
        validateUniqueEmail(request.getEmail(), null);

        Role lecturerRole = roleService.findRoleByCode(RoleCodes.LECTURER);

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(lecturerRole)
                .provider(AuthProvider.LOCAL)
                .isActive(request.getActive() == null || request.getActive())
                .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : true)
                .build();

        return LecturerMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public LecturerResponse update(UUID id, LecturerUpdateRequest request) {
        User user = findLecturer(id);

        if (request.getFullName() != null) {
            if (request.getFullName().isBlank()) {
                throw new BadRequestException("Full name cannot be blank");
            }
            user.setFullName(request.getFullName().trim());
        }

        if (request.getEmail() != null) {
            if (request.getEmail().isBlank()) {
                throw new BadRequestException("Email cannot be blank");
            }
            String newEmail = request.getEmail().trim().toLowerCase();
            validateUniqueEmail(newEmail, id);
            user.setEmail(newEmail);
        }

        if (request.getPassword() != null) {
            if (request.getPassword().isBlank()) {
                throw new BadRequestException("Password cannot be blank");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setProvider(AuthProvider.LOCAL);
        }

        if (request.getActive() != null) {
            user.setIsActive(request.getActive());
        }

        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }

        return LecturerMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = findLecturer(id);

        if (documentRepository.existsByUploadedBy_Id(id)) {
            throw new BadRequestException("Cannot delete lecturer that has uploaded documents");
        }

        userRepository.delete(user);
    }

    @Transactional
    public LecturerResponse toggleActive(UUID id) {
        User user = findLecturer(id);
        user.setIsActive(user.getIsActive() == null || !user.getIsActive());
        return LecturerMapper.toResponse(userRepository.save(user));
    }

    private User findLecturer(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer not found"));

        if (!RoleCodes.LECTURER.equals(user.getRole().getCode())) {
            throw new ResourceNotFoundException("Lecturer not found");
        }

        return user;
    }

    private void validateUniqueEmail(String email, UUID excludeId) {
        boolean duplicate = excludeId == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndIdNot(email, excludeId);
        if (duplicate) {
            throw new BadRequestException("Email already exists");
        }
    }
}
