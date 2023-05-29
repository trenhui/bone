//package com.bone.core.domain;
//
//import org.springframework.data.domain.ReactiveAuditorAware;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.ReactiveSecurityContextHolder;
//import org.springframework.security.core.context.SecurityContext;
//import org.springframework.security.core.userdetails.User;
//
//
//public class SpringSecurityReactiveAuditorAware implements ReactiveAuditorAware<User> {
//
//    @Override
//    public Mono<User> getCurrentAuditor() {
//
//        return ReactiveSecurityContextHolder.getContext()
//                .map(SecurityContext::getAuthentication)
//                .filter(Authentication::isAuthenticated)
//                .map(Authentication::getPrincipal)
//                .map(User.class::cast);
//    }
//}