package com.keyStone.Playroom021.config;

import com.keyStone.Playroom021.security.CustomUserDetailsService;
import com.keyStone.Playroom021.security.JwtAuthEntryPoint;
import com.keyStone.Playroom021.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Step 10: OpenAPI document + Swagger UI (public; "Authorize" in the UI takes a JWT).
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .requestMatchers("/", "/index.html", "/login.html", "/signup.html",
                                "/dashboard.html", "/customer-portal.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                        // ---- Step 4: Customer + Site management ----
                        // Role gate here; CUSTOMER row-level scoping (own data only) is in CustomerService/SiteService.
                        .requestMatchers(HttpMethod.GET, "/api/customers/me").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/customers").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/customers").hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/customers/*").hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER")
                        .requestMatchers(HttpMethod.PUT, "/api/customers/*").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/*").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/customers/*/sites").hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/customers/*/sites").hasAnyRole("MANAGER", "DISPATCHER", "CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/sites", "/api/sites/*").hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER")
                        .requestMatchers(HttpMethod.PUT, "/api/sites/*").hasAnyRole("MANAGER", "DISPATCHER", "CUSTOMER")
                        .requestMatchers(HttpMethod.DELETE, "/api/sites/*").hasRole("MANAGER")
                        // ---- Step 5: Work Order management + lifecycle ----
                        // Role gate here; CUSTOMER (own only) and TECHNICIAN (assigned only)
                        // row-level scoping is enforced in WorkOrderService.
                        .requestMatchers(HttpMethod.POST, "/api/work-orders").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/work-orders", "/api/work-orders/*")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN", "CUSTOMER")
                        .requestMatchers(HttpMethod.PUT, "/api/work-orders/*").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/work-orders/*").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/work-orders/*/status")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        // ---- Step 6: technician job actions, parts, part usage, time logs ----
                        // Role gate here; technician = assigned work orders only is enforced in the services.
                        .requestMatchers(HttpMethod.POST, "/api/work-orders/*/start", "/api/work-orders/*/hold",
                                "/api/work-orders/*/resume", "/api/work-orders/*/complete").hasRole("TECHNICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/parts").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/parts", "/api/parts/*")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/parts/*").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/parts/*").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/work-orders/*/part-usage")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/work-orders/*/part-usage")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.DELETE, "/api/work-orders/*/part-usage/*")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/work-orders/*/time-logs").hasRole("TECHNICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/work-orders/*/time-logs")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        .requestMatchers(HttpMethod.DELETE, "/api/work-orders/*/time-logs/*")
                                .hasAnyRole("MANAGER", "DISPATCHER", "TECHNICIAN")
                        // ---- Step 7: dashboard/report APIs (staff who manage work) ----
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/summary", "/api/dashboard/sla",
                                "/api/dashboard/technician-workload").hasAnyRole("MANAGER", "DISPATCHER")
                        .requestMatchers("/api/dashboard/manager").hasRole("MANAGER")
                        .requestMatchers("/api/dashboard/customer").hasRole("CUSTOMER")
                        .requestMatchers("/api/dashboard/worker").hasRole("TECHNICIAN")
                        .requestMatchers("/api/dashboard/dispatcher").hasRole("DISPATCHER")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
