package com.edifika.payment.shared.infrastructure.persistence.jpa.configuration.strategy;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import static io.github.encryptorcode.pluralize.Pluralize.pluralize;

/**
 * Estrategia de nombres de Hibernate: convierte tablas y columnas a snake_case
 * y pluraliza los nombres de tabla (ej. PaymentTransaction → payment_transactions).
 */
public class SnakeCaseWithPluralizedTablePhysicalNamingStrategy implements PhysicalNamingStrategy {

    /**
     * Converts the identifier to Snake Case.
     * @param identifier object identifier
     * @return Snake Case identifier
     */
    private Identifier toSnakeCase(final Identifier identifier) {
        if (identifier == null) return null;

        final String regex = "([a-z])([A-Z])";
        final String replacement = "$1_$2";
        final String newName = identifier.getText()
                .replaceAll(regex, replacement)
                .toLowerCase();
        return Identifier.toIdentifier(newName);
    }
    /**
     * Pluralizes the identifier
     * @param identifier object identifier
     * @return Pluralized identifier
     */
    private Identifier toPlural(final Identifier identifier) {
        final String newName = pluralize(identifier.getText());
        return Identifier.toIdentifier(newName);
    }
    /**
     * Converts the Schema Name to Snake Case.
     * @param identifier schema name
     * @param jdbcEnvironment jdbc environment
     * @return Snake Case schema name
     */
    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return this.toSnakeCase(identifier);
    }

    /**
     * Converts The Schema Name to Snake Case.
     * @param identifier sequence name
     * @param jdbcEnvironment jdbc environment
     * @return Snake Case schema name
     */
    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return this.toSnakeCase(identifier);
    }

    /**
     * Converts the Table Name to Snake Case and Pluralizes it.
     * @param identifier table name
     * @param jdbcEnvironment jdbc environment
     * @return Snake Case and Pluralized table name
     */
    @Override
    public Identifier toPhysicalTableName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return this.toSnakeCase(toPlural(identifier));
    }

    /**
     * Converts the Sequence Name to Snake Case.
     * @param identifier sequence name
     * @param jdbcEnvironment jdbc environment
     * @return Snake Case sequence name
     */
    @Override
    public Identifier toPhysicalSequenceName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return this.toSnakeCase(identifier);
    }

    /**
     * Converts the Column Name to Snake Case.
     * @param identifier column name
     * @param jdbcEnvironment jdbc environment
     * @return Snake Case column name
     */
    @Override
    public Identifier toPhysicalColumnName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return this.toSnakeCase(identifier);
    }
}
