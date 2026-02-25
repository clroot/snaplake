package com.snaplake.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import org.springframework.core.io.Resource

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(SpaFallbackResourceResolver())
    }
}

/**
 * SPA fallback: /api, /health 이외의 요청에서 정적 리소스를 찾지 못하면
 * index.html로 포워딩하여 클라이언트 사이드 라우팅을 지원합니다.
 */
class SpaFallbackResourceResolver : PathResourceResolver() {

    override fun getResource(resourcePath: String, location: Resource): Resource? {
        val requested = super.getResource(resourcePath, location)
        if (requested != null) {
            return requested
        }

        // API, health 엔드포인트는 fallback하지 않음
        if (resourcePath.startsWith("api/") || resourcePath.startsWith("health")) {
            return null
        }

        val indexHtml = ClassPathResource("/static/index.html")
        return if (indexHtml.exists()) indexHtml else null
    }
}
