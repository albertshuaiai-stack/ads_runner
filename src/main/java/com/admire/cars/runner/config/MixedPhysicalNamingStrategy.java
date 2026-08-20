package com.admire.cars.runner.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.util.Locale;

public class MixedPhysicalNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        if (name == null) {
            return null;
        }
        String text = name.getText();
        if (text != null && text.toUpperCase(Locale.ROOT).startsWith("QRTZ_")) {
            return Identifier.toIdentifier(text, name.isQuoted());
        }
        return Identifier.toIdentifier(text == null ? null : text.toLowerCase(Locale.ROOT), name.isQuoted());
    }
}
