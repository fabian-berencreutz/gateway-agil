package se.iths.fabian.gatewayagil.dto;

import java.util.List;

public record AuthServiceResponse(
        String accessToken,
        long expiresIn,
        String subject,
        List<String> roles
) {
}
