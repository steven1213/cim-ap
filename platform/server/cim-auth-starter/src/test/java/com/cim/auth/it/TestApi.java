package com.cim.auth.it;

import com.cim.auth.principal.CimUserPrincipal;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestApi {

    private final TestService svc;

    public TestApi(TestService svc) {
        this.svc = svc;
    }

    @GetMapping("/public")
    public String pub() {
        return "ok";
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public String me(Authentication authentication) {
        return ((CimUserPrincipal) authentication.getPrincipal()).userId();
    }

    @GetMapping("/order")
    @PreAuthorize("hasAuthority('mds:order:read')")
    public String order() {
        return "order-ok";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('mds:order:admin')")
    public String admin() {
        return "admin-ok";
    }

    @GetMapping("/perm")
    @PreAuthorize("hasPermission('order', 'mds:order:read')")
    public String perm() {
        return "perm-ok";
    }

    @GetMapping("/scope")
    public String scope() {
        return svc.currentTenant();
    }
}
