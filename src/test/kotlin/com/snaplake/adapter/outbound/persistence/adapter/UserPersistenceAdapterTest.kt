package com.snaplake.adapter.outbound.persistence.adapter

import com.snaplake.application.port.outbound.LoadUserPort
import com.snaplake.application.port.outbound.SaveUserPort
import com.snaplake.domain.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@SpringBootTest
class UserPersistenceAdapterTest {

    @Autowired
    lateinit var saveUserPort: SaveUserPort

    @Autowired
    lateinit var loadUserPort: LoadUserPort

    @Test
    fun `save and find user by username`() {
        val username = "admin-${UUID.randomUUID()}"
        val user = User.create(username = username, passwordHash = "hashed")
        saveUserPort.save(user)

        val found = loadUserPort.findByUsername(username)
        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo(username)
    }

    @Test
    fun `existsAny returns true when users exist`() {
        val username = "testuser-${UUID.randomUUID()}"
        val user = User.create(username = username, passwordHash = "hashed")
        saveUserPort.save(user)

        assertThat(loadUserPort.existsAny()).isTrue()
    }

    @Test
    fun `findByUsername returns null for non-existing user`() {
        val found = loadUserPort.findByUsername("nonexistent-${UUID.randomUUID()}")
        assertThat(found).isNull()
    }
}
