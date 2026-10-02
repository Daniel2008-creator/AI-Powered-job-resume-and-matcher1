package com.talentlens.api;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final String databasePath;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(@Value("${talentlens.auth-db:talentlens.db}") String databasePath) {
        this.databasePath = databasePath;
    }

    @PostConstruct
    void initialize() {
        try {
            Path path = Path.of(databasePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            password_hash TEXT NOT NULL,
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS sessions (
                            token TEXT PRIMARY KEY,
                            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )
                        """);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize the account database", error);
        }
    }

    public Map<String, Object> createUser(String email, String password) {
        String normalizedEmail = email.strip().toLowerCase();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalizedEmail);
            statement.setString(2, hashPassword(password));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No user id was generated");
                }
                return user(keys.getLong(1), normalizedEmail);
            }
        } catch (SQLException error) {
            if (error.getMessage() != null && error.getMessage().toLowerCase().contains("unique constraint")) {
                throw new ApiException(HttpStatus.CONFLICT, "An account with that email already exists");
            }
            throw new IllegalStateException("Could not create account", error);
        }
    }

    public Map<String, Object> createSession(String email, String password) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, email, password_hash FROM users WHERE email = ?")) {
            statement.setString(1, email.strip().toLowerCase());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !verifyPassword(password, result.getString("password_hash"))) {
                    return null;
                }
                String token = newToken();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO sessions (token, user_id) VALUES (?, ?)")) {
                    insert.setString(1, token);
                    insert.setLong(2, result.getLong("id"));
                    insert.executeUpdate();
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("token", token);
                response.put("user", user(result.getLong("id"), result.getString("email")));
                return response;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not create session", error);
        }
    }

    public Map<String, Object> userForToken(String token) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT users.id, users.email FROM users JOIN sessions " +
                             "ON sessions.user_id = users.id WHERE sessions.token = ?")) {
            statement.setString(1, token);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? user(result.getLong("id"), result.getString("email")) : null;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not validate session", error);
        }
    }

    public void deleteSession(String token) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            statement.setString(1, token);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not sign out", error);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + Path.of(databasePath).toAbsolutePath());
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = deriveKey(password, salt);
        return HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(hash);
    }

    private boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split("\\$", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(parts[1]);
            return MessageDigest.isEqual(expected, deriveKey(password, HexFormat.of().parseHex(parts[0])));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private byte[] deriveKey(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("Could not hash password", error);
        } finally {
            spec.clearPassword();
        }
    }

    private String newToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private Map<String, Object> user(long id, String email) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("email", email);
        return user;
    }
}