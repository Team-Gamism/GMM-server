package com.gmm.global.config

import com.gmm.global.auth.CurrentUserArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val properties: GmmProperties,
    private val currentUserArgumentResolver: CurrentUserArgumentResolver,
) : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            API_PREFIX,
            HandlerTypePredicate
                .forBasePackage("com.gmm.domain")
                .and(HandlerTypePredicate.forAnnotation(RestController::class.java)),
        )
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = properties.cors.allowedOrigins.filter { it.isNotBlank() }
        if (origins.isEmpty()) return
        registry
            .addMapping("$API_PREFIX/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
            .allowCredentials(true)
            .maxAge(3600)
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserArgumentResolver)
    }

    companion object {
        const val API_PREFIX = "/api/v1"
    }
}
