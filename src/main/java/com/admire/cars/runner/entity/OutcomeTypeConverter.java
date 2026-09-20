package com.admire.cars.runner.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OutcomeTypeConverter implements AttributeConverter<OutcomeType, String> {

    @Override
    public String convertToDatabaseColumn(OutcomeType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public OutcomeType convertToEntityAttribute(String dbData) {
        return OutcomeType.fromString(dbData);
    }
}
