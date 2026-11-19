package com.gmm.domain.game.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Stores a short enum list as comma-separated enum names, in the given order. */
abstract class EnumListConverter<E : Enum<E>>(private val parse: (String) -> E) : AttributeConverter<List<E>, String> {
    override fun convertToDatabaseColumn(attribute: List<E>?): String =
        attribute.orEmpty().joinToString(",") { it.name }

    override fun convertToEntityAttribute(dbData: String?): List<E> =
        dbData.orEmpty().split(",").filter { it.isNotBlank() }.map(parse)
}

@Converter
class PlatformListConverter : EnumListConverter<Platform>(Platform::valueOf)

@Converter
class InputDeviceListConverter : EnumListConverter<InputDevice>(InputDevice::valueOf)
