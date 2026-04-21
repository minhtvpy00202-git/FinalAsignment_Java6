package com.poly.ASM.config;

import com.poly.ASM.security.CartMergeAuthSuccessHandler;
import com.poly.ASM.security.GoogleOAuth2LoginSuccessHandler;
import com.poly.ASM.security.JwtAuthenticationFilter;
import com.poly.ASM.security.LegacyCompatiblePasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LegacyCompatiblePasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Chuỗi lọc bảo mật cho API REST:
     * - stateless JWT
     * - role-based theo endpoint
     * - disable form login/oauth2 tại nhánh /api/**.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.securityMatcher("/api/**");
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/store/products/**").permitAll()
                .requestMatchers("/api/auth/session/login-info").permitAll()
                .requestMatchers("/api/order-workflow/payos/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers("/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/orders/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/orders/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/orders/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/account/sign-up", "/api/account/forgot-password").permitAll()
                .requestMatchers("/api/account/**", "/api/cart/**", "/api/notifications/**", "/api/order-workflow/**", "/api/reviews/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
        );
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.formLogin(form -> form.disable());
        http.oauth2Login(oauth2 -> oauth2.disable());
        http.logout(logout -> logout.disable());
        return http.build();
    }

    /**
     * Chuỗi lọc bảo mật cho web route (MVC/SPA entry):
     * - hỗ trợ form login và OAuth2 login
     * - giới hạn /admin/** theo role ADMIN.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http,
                                                      CartMergeAuthSuccessHandler successHandler,
                                                      GoogleOAuth2LoginSuccessHandler googleSuccessHandler,
                                                      JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/home/**",
                        "/product/**",
                        "/auth/login",
                        "/account/sign-up",
                        "/account/forgot-password",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/images/**",
                        "/css/**",
                        "/js/**",
                        "/error"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/order/**", "/account/**", "/notifications/**").authenticated()
                .anyRequest().permitAll()
        );

        http.formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .successHandler(successHandler)
                .failureUrl("/auth/login?error=1")
                .defaultSuccessUrl("/home/index", true)
                .permitAll()
        );

        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/auth/login")
                .successHandler(googleSuccessHandler)
                .failureUrl("/auth/login?error=1")
        );

        http.logout(logout -> logout.disable());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
