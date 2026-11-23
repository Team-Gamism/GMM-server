package com.gmm.domain.jam.presentation

import com.gmm.domain.jam.service.JamService
import com.gmm.global.auth.AuthUser
import com.gmm.global.auth.CurrentUser
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Jam", description = "게임잼")
@RestController
@RequestMapping("/jams")
class JamController(private val jamService: JamService) {
    @GetMapping
    fun list(): List<JamResponse> = jamService.list()

    @GetMapping("/{jamId}")
    fun get(@PathVariable jamId: String): JamResponse = jamService.get(jamId)

    @PostMapping
    fun create(@CurrentUser user: AuthUser, @RequestBody request: JamRequest): ResponseEntity<JamResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(jamService.create(user, request.toDetails()))

    @PutMapping("/{jamId}")
    fun update(
        @CurrentUser user: AuthUser,
        @PathVariable jamId: String,
        @RequestBody request: JamRequest,
    ): JamResponse = jamService.update(user, jamId, request.toDetails())

    @DeleteMapping("/{jamId}")
    fun delete(@CurrentUser user: AuthUser, @PathVariable jamId: String): ResponseEntity<Void> {
        jamService.delete(user, jamId)
        return ResponseEntity.noContent().build()
    }
}
