package com.snaplake.adapter.inbound.web

import com.snaplake.domain.exception.DatasourceNotFoundException
import com.snaplake.domain.exception.InvalidCredentialsException
import com.snaplake.domain.exception.SystemAlreadyInitializedException
import com.snaplake.domain.vo.DatasourceId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class GlobalExceptionHandlerTest : DescribeSpec({

    val handler = GlobalExceptionHandler()

    describe("handleDomainException") {
        it("SystemAlreadyInitializedException은 409 CONFLICT를 반환한다") {
            val response = handler.handleDomainException(SystemAlreadyInitializedException())
            response.statusCode shouldBe HttpStatus.CONFLICT
            response.body?.error?.code shouldBe "SYSTEM_ALREADY_INITIALIZED"
        }

        it("DatasourceNotFoundException은 404 NOT_FOUND를 반환한다") {
            val response = handler.handleDomainException(DatasourceNotFoundException(DatasourceId("test")))
            response.statusCode shouldBe HttpStatus.NOT_FOUND
            response.body?.error?.code shouldBe "DATASOURCE_NOT_FOUND"
        }

        it("InvalidCredentialsException은 401 UNAUTHORIZED를 반환한다") {
            val response = handler.handleDomainException(InvalidCredentialsException())
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            response.body?.error?.code shouldBe "INVALID_CREDENTIALS"
        }
    }

    describe("handleIllegalArgument") {
        it("400 BAD_REQUEST를 반환한다") {
            val response = handler.handleIllegalArgument(IllegalArgumentException("Invalid input"))
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            response.body?.error?.code shouldBe "BAD_REQUEST"
        }
    }

    describe("handleGenericException") {
        it("500 INTERNAL_SERVER_ERROR를 반환한다") {
            val response = handler.handleGenericException(RuntimeException("unexpected"))
            response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            response.body?.error?.code shouldBe "INTERNAL_ERROR"
        }
    }
})
