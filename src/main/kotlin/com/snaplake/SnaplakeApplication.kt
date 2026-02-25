package com.snaplake

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SnaplakeApplication

fun main(args: Array<String>) {
    runApplication<SnaplakeApplication>(*args)
}
