package com.gmm.global.config

import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "GMM API",
        description = """게이미즘 게임잼 서비스 API예요.

- 인증: DataGSM 로그인 후 서버가 발급한 `GMM_SESSION` 쿠키를 보내요. 브라우저는 `credentials: 'include'`로 요청해요.
- 오류: HTTP 상태 코드와 `{ "code": "...", "message": "..." }` 본문을 보내요. `message`는 사용자에게 그대로 보여도 되는 해요체 문장이에요.
- 날짜: ISO 8601 UTC 문자열이에요.""",
        version = "v1",
    ),
    security = [SecurityRequirement(name = "SessionCookie")],
    // Same origin as the page that shows the docs. springdoc would otherwise build http://<host> from the proxied
    // request, and the browser blocks "Try it out" calls from the HTTPS page to that URL.
    servers = [Server(url = "/", description = "이 문서를 연 서버")],
)
@SecurityScheme(
    name = "SessionCookie",
    type = SecuritySchemeType.APIKEY,
    `in` = SecuritySchemeIn.COOKIE,
    paramName = "GMM_SESSION",
    description = "DataGSM 로그인 후 발급되는 세션 쿠키",
)
class OpenApiConfig {
    init {
        // The signed-in user comes from the session cookie, not from request parameters.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser::class.java)
    }
}
