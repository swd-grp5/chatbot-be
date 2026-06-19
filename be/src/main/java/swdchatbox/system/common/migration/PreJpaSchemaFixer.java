package swdchatbox.system.common.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class PreJpaSchemaFixer implements BeanPostProcessor {

    private final AtomicBoolean applied = new AtomicBoolean(false);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (applied.get() || !"dataSource".equals(beanName) || !(bean instanceof DataSource dataSource)) {
            return bean;
        }
        applied.set(true);

        try {
            SchemaMigrationSupport.fixUserRoleIds(new JdbcTemplate(dataSource));
        } catch (Exception ex) {
            log.warn("Pre-JPA schema fix failed: {}", ex.getMessage());
        }

        return bean;
    }
}
