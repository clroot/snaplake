package com.snaplake.adapter.inbound.web

import com.snaplake.adapter.outbound.persistence.repository.UserJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class SetupControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @BeforeEach
    fun setUp() {
        userJpaRepository.deleteAll()
    }

    @Nested
    @DisplayName("GET /api/setup/status")
    inner class GetStatus {
        @Test
        fun `초기화 전에는 initialized=false를 반환한다`() {
            mockMvc.get("/api/setup/status")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.initialized") { value(false) }
                }
        }
    }

    @Nested
    @DisplayName("POST /api/setup/initialize")
    inner class Initialize {
        @Test
        fun `시스템을 초기화한다`() {
            mockMvc.post("/api/setup/initialize") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "adminUsername": "admin",
                        "adminPassword": "password123",
                        "storageType": "LOCAL",
                        "localPath": "./data/snapshots"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }

            mockMvc.get("/api/setup/status")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.initialized") { value(true) }
                }
        }
    }
}
