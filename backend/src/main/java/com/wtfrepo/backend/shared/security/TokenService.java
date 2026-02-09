package com.wtfrepo.backend.shared.security;

import com.wtfrepo.backend.auth.domain.AuthUser;

public interface TokenService {

  IssuedToken issueToken(AuthUser user);
}

