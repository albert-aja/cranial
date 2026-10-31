package io.cranial.platform.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApplicationAuthenticationToken extends AbstractAuthenticationToken {

    private final VerifiedRequestContext context;

    public ApplicationAuthenticationToken(VerifiedRequestContext context) {
        super(List.of(new SimpleGrantedAuthority("ROLE_APPLICATION")));
        this.context = context;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "[REDACTED]";
    }

    @Override
    public VerifiedRequestContext getPrincipal() {
        return context;
    }

    public VerifiedRequestContext context() {
        return context;
    }
}

