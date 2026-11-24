package com.gmm.global.auth

import com.gmm.global.exception.ErrorCode
import com.gmm.global.exception.GmmException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            parameter.parameterType == AuthUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val user =
            webRequest.getAttribute(SessionAuthenticationFilter.AUTH_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                as AuthUser?
        val required = parameter.getParameterAnnotation(CurrentUser::class.java)?.required ?: true
        if (user == null && required) throw GmmException(ErrorCode.UNAUTHORIZED)
        return user
    }
}
