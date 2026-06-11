package swdchatbox.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles(user.getRole().getCode())
                .disabled(!user.getIsActive())
                .build();
    }
}