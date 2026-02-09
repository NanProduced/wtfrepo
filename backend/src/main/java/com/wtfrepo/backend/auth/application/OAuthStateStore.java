package com.wtfrepo.backend.auth.application;

public interface OAuthStateStore {

  boolean consumeOnce(String oauthState);
}

