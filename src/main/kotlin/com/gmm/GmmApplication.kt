package com.gmm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class GmmApplication

fun main(args: Array<String>) {
    runApplication<GmmApplication>(*args)
}
