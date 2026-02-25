package com.snaplake.domain.model

import com.snaplake.domain.vo.UserId
import java.time.Instant

enum class UserRole { ADMIN }

class User private constructor(
    val id: UserId,
    val username: String,
    val passwordHash: String,
    val role: UserRole,
    val createdAt: Instant,
) {
    companion object {
        fun create(
            username: String,
            passwordHash: String,
            role: UserRole = UserRole.ADMIN,
        ): User {
            require(username.isNotBlank()) { "Username must not be blank" }
            return User(
                id = UserId.generate(),
                username = username.trim(),
                passwordHash = passwordHash,
                role = role,
                createdAt = Instant.now(),
            )
        }

        fun reconstitute(
            id: UserId,
            username: String,
            passwordHash: String,
            role: UserRole,
            createdAt: Instant,
        ): User = User(id, username, passwordHash, role, createdAt)
    }
}
