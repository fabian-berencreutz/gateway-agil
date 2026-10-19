package se.iths.fabian.gatewayagil.dto;

import java.util.List;

public record LoginResponse(
        long expiresIn,
        String subject,
        List<String> roles
) {
}
