package swdchatbox.system.student.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import swdchatbox.system.chat.repository.ChatConversationRepository;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.common.exception.BadRequestException;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.entity.Role;
import swdchatbox.system.role.service.RoleService;
import swdchatbox.system.student.dto.request.StudentFilterRequest;
import swdchatbox.system.student.dto.request.StudentRequest;
import swdchatbox.system.student.dto.request.StudentUpdateRequest;
import swdchatbox.system.student.dto.response.StudentResponse;
import swdchatbox.system.student.mapper.StudentMapper;
import swdchatbox.system.subscription.repository.StudentSubscriptionRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.AuthProvider;
import swdchatbox.system.user.repository.UserRepository;
import swdchatbox.system.user.repository.UserSpecifications;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final StudentSubscriptionRepository studentSubscriptionRepository;
    private final ChatConversationRepository chatConversationRepository;

    public PageResponse<StudentResponse> findAll(StudentFilterRequest filter, Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecifications.hasRoleCode(RoleCodes.STUDENT))
                .and(UserSpecifications.hasActive(filter != null ? filter.getActive() : null))
                .and(UserSpecifications.keywordLike(filter != null ? filter.getKeyword() : null))
                .and(UserSpecifications.createdAfter(filter != null ? filter.getCreatedFrom() : null))
                .and(UserSpecifications.createdBefore(filter != null ? filter.getCreatedTo() : null));

        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.<StudentResponse>builder()
                .content(page.getContent().stream().map(StudentMapper::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    public StudentResponse findById(UUID id) {
        return StudentMapper.toResponse(findStudent(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        validateUniqueEmail(request.getEmail(), null);

        Role studentRole = roleService.findRoleByCode(RoleCodes.STUDENT);

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(studentRole)
                .provider(AuthProvider.LOCAL)
                .isActive(request.getActive() == null || request.getActive())
                .emailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : true)
                .build();

        return StudentMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public StudentResponse update(UUID id, StudentUpdateRequest request) {
        User user = findStudent(id);

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

        return StudentMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        User user = findStudent(id);

        if (studentSubscriptionRepository.existsByStudent_Id(id)) {
            throw new BadRequestException("Cannot delete student that has subscriptions");
        }

        if (chatConversationRepository.countByUser_Id(id) > 0) {
            throw new BadRequestException("Cannot delete student that has chat conversations");
        }

        userRepository.delete(user);
    }

    @Transactional
    public StudentResponse toggleActive(UUID id) {
        User user = findStudent(id);
        user.setIsActive(user.getIsActive() == null || !user.getIsActive());
        return StudentMapper.toResponse(userRepository.save(user));
    }

    private User findStudent(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (!RoleCodes.STUDENT.equals(user.getRole().getCode())) {
            throw new ResourceNotFoundException("Student not found");
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
