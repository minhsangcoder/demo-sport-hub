package com.sporthub.identity.config;

import com.sporthub.identity.domain.*;
import com.sporthub.identity.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(name="sporthub.demo.seed-enabled", havingValue="true")
public class DemoIdentitySeeder implements ApplicationRunner {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final String demoPassword;
    public DemoIdentitySeeder(UserRepository users, UserProfileRepository profiles, PasswordEncoder passwords,
            @Value("${sporthub.demo.password}") String demoPassword) {
        this.users=users; this.profiles=profiles; this.passwords=passwords; this.demoPassword=demoPassword;
        if(demoPassword.length()<12) throw new IllegalStateException("DEMO_PASSWORD must contain at least 12 characters");
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        seed("admin@sporthub.local", "Quản trị demo", Set.of(Role.ADMIN, Role.CUSTOMER));
        seed("owner@sporthub.local", "Chủ cơ sở demo", Set.of(Role.OWNER, Role.CUSTOMER));
        seed("owner2@sporthub.local", "Hoàng Văn Nam (Chủ Sân Ba Đình)", Set.of(Role.OWNER, Role.CUSTOMER));
        seed("owner3@sporthub.local", "Trần Thị Mai Phương (Chủ Sân Tây Hồ)", Set.of(Role.OWNER, Role.CUSTOMER));
        seed("owner4@sporthub.local", "Lê Hoàng Long (Chủ Cơ Sở Mới)", Set.of(Role.OWNER, Role.CUSTOMER));
        seed("staff@sporthub.local", "Nhân viên demo", Set.of(Role.STAFF));
        seed("staff.badinh@sporthub.local", "Nguyễn Văn Tuấn (Lễ tân Ba Đình)", Set.of(Role.STAFF));
        seed("staff.tayho@sporthub.local", "Lê Thu Hà (Lễ tân Tây Hồ)", Set.of(Role.STAFF));
        seed("customer@sporthub.local", "Khách hàng demo", Set.of(Role.CUSTOMER));
        seed("customer2@sporthub.local", "Khách hàng thứ hai", Set.of(Role.CUSTOMER));
        seed("demo.anh@sporthub.local", "Nguyễn Minh Anh (Demo)", Set.of(Role.CUSTOMER));
        seed("demo.huy@sporthub.local", "Trần Quốc Huy (Demo)", Set.of(Role.CUSTOMER));
        seed("demo.linh@sporthub.local", "Lê Thùy Linh (Demo)", Set.of(Role.CUSTOMER));
        seed("demo.trang@sporthub.local", "Phạm Thu Trang (Demo)", Set.of(Role.CUSTOMER));
        seed("demo.quang@sporthub.local", "Vũ Minh Quang (Demo)", Set.of(Role.CUSTOMER));
        seed("demo.mai@sporthub.local", "Đỗ Ngọc Mai (Demo)", Set.of(Role.CUSTOMER));
    }
    private void seed(String email, String name, Set<Role> roles) {
        if(users.existsByEmailIgnoreCase(email)) return;
        var user=users.save(User.builder().email(email).passwordHash(passwords.encode(demoPassword))
            .status(AccountStatus.ACTIVE).emailVerified(true).roles(new java.util.LinkedHashSet<>(roles)).build());
        profiles.save(UserProfile.builder().user(user).fullName(name).build());
    }
}
