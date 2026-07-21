package com.meridian.claims.service;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordService {

    private final SecurityPolicy securityPolicy;

    // Complexity: at least one digit and one special character, plus the
    // configurable minimum length. Built once from the policy minimum length.
    private final Pattern complexity;

    @Autowired
    public PasswordService(SecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
        int minLength = securityPolicy.getPasswordMinLength();
        this.complexity = Pattern.compile(
            "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{" + minLength + ",}$");
    }

    public String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(securityPolicy.getBcryptRounds()));
    }

    public boolean verify(String plaintext, String hash) {
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean meetsComplexity(String password) {
        if (password == null) {
            return false;
        }
        return complexity.matcher(password).matches();
    }

    public String complexityMessage() {
        return "Password must be at least " + securityPolicy.getPasswordMinLength()
            + " characters and contain at least one number and one special character.";
    }
}
