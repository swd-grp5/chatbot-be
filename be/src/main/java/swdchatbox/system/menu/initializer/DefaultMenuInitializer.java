package swdchatbox.system.menu.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.role.RoleCodes;
import swdchatbox.system.menu.entity.MenuGroup;
import swdchatbox.system.menu.entity.MenuItem;
import swdchatbox.system.menu.repository.MenuGroupRepository;

@Component
@Order(20)
@RequiredArgsConstructor
public class DefaultMenuInitializer implements CommandLineRunner {

    private final MenuGroupRepository menuGroupRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (menuGroupRepository.count() > 0) {
            return;
        }

        // 1. Student — học tập
        MenuGroup studentLearning = saveGroup("Học tập", "Chat và tài liệu của sinh viên", 1);
        addItem(studentLearning, "Chat", "/", "MessageSquare", 1, RoleCodes.STUDENT);
        addItem(studentLearning, "Tài liệu", "/documents", "FileText", 2, RoleCodes.STUDENT);

        // 2. Student — tài chính
        MenuGroup studentFinance = saveGroup("Tài chính", "Ví và gói đăng ký của sinh viên", 2);
        addItem(studentFinance, "Ví", "/wallet", "Wallet", 1, RoleCodes.STUDENT);
        addItem(studentFinance, "Gói tháng", "/subscriptions", "CreditCard", 2, RoleCodes.STUDENT);

        // 3. Lecturer
        MenuGroup lecturer = saveGroup("Giảng dạy", "Chức năng dành cho giảng viên", 3);
        addItem(lecturer, "Tài liệu", "/lecturer/documents", "FileText", 1, RoleCodes.LECTURER);

        // 4. Admin — người dùng
        MenuGroup adminUsers = saveGroup("Quản trị người dùng", "Quản lý sinh viên, giảng viên và vai trò", 4);
        addItem(adminUsers, "Sinh viên", "/admin/users", "Users", 1, RoleCodes.ADMIN);
        addItem(adminUsers, "Giảng viên", "/admin/lecturers", "GraduationCap", 2, RoleCodes.ADMIN);
        addItem(adminUsers, "Vai trò", "/admin/roles", "ShieldCheck", 3, RoleCodes.ADMIN);

        // 5. Admin — hệ thống
        MenuGroup adminSystem = saveGroup("Hệ thống", "Môn học, gói tháng và cấu hình AI", 5);
        addItem(adminSystem, "Môn học", "/admin/subjects", "BookOpen", 1, RoleCodes.ADMIN);
        addItem(adminSystem, "Gói tháng", "/admin/subscriptions", "CreditCard", 2, RoleCodes.ADMIN);
        addItem(adminSystem, "Cấu hình AI", "/admin/ai-config", "Bot", 3, RoleCodes.ADMIN);

        menuGroupRepository.save(studentLearning);
        menuGroupRepository.save(studentFinance);
        menuGroupRepository.save(lecturer);
        menuGroupRepository.save(adminUsers);
        menuGroupRepository.save(adminSystem);
    }

    private MenuGroup saveGroup(String name, String description, int displayOrder) {
        return MenuGroup.builder()
                .name(name)
                .description(description)
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }

    private void addItem(
            MenuGroup group,
            String title,
            String url,
            String icon,
            int displayOrder,
            String requiredRole
    ) {
        MenuItem item = MenuItem.builder()
                .menuGroup(group)
                .title(title)
                .url(url)
                .icon(icon)
                .displayOrder(displayOrder)
                .active(true)
                .requiredRole(requiredRole)
                .build();
        group.getItems().add(item);
    }
}
