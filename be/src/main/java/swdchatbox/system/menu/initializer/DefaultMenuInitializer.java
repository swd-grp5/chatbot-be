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

        // 1. Student — learning
        MenuGroup studentLearning = saveGroup("Learning", "Student chat and documents", 1);
        addItem(studentLearning, "Chat", "/", "MessageSquare", 1, RoleCodes.STUDENT);
        addItem(studentLearning, "Documents", "/documents", "FileText", 2, RoleCodes.STUDENT);

        // 2. Student — finance
        MenuGroup studentFinance = saveGroup("Finance", "Student wallet and subscription plans", 2);
        addItem(studentFinance, "Wallet", "/wallet", "Wallet", 1, RoleCodes.STUDENT);
        addItem(studentFinance, "Subscriptions", "/subscriptions", "CreditCard", 2, RoleCodes.STUDENT);

        // 3. Lecturer
        MenuGroup lecturer = saveGroup("Teaching", "Lecturer features", 3);
        addItem(lecturer, "Documents", "/lecturer/documents", "FileText", 1, RoleCodes.LECTURER);

        // 4. Admin — users
        MenuGroup adminUsers = saveGroup("User Management", "Manage students, lecturers, and roles", 4);
        addItem(adminUsers, "Students", "/admin/users", "Users", 1, RoleCodes.ADMIN);
        addItem(adminUsers, "Lecturers", "/admin/lecturers", "GraduationCap", 2, RoleCodes.ADMIN);
        addItem(adminUsers, "Roles", "/admin/roles", "ShieldCheck", 3, RoleCodes.ADMIN);

        // 5. Admin — system
        MenuGroup adminSystem = saveGroup("System", "Subjects, subscriptions, and AI config", 5);
        addItem(adminSystem, "Subjects", "/admin/subjects", "BookOpen", 1, RoleCodes.ADMIN);
        addItem(adminSystem, "Subscriptions", "/admin/subscriptions", "CreditCard", 2, RoleCodes.ADMIN);
        addItem(adminSystem, "AI Config", "/admin/ai-config", "Bot", 3, RoleCodes.ADMIN);

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
