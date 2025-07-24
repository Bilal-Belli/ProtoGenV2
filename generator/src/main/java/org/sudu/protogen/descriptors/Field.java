package org.sudu.protogen.descriptors;

import com.google.protobuf.Descriptors;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.sudu.protogen.Options;

import java.util.Objects;
import java.util.Optional;

public class Field implements Descriptor {

    private Descriptors.FieldDescriptor descriptor;
    private boolean needGetterInOriginalClass = false;

    public boolean getNeedGetterInOriginalClass() {
        return this.needGetterInOriginalClass;
    }
    public void setNeedGetterInOriginalClass(boolean value) {
        this.needGetterInOriginalClass = value;
    }
    public Field(Descriptors.FieldDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public String getName() {
        String originalName = descriptor.getName();
        // If the original name starts with underscore, remove it to match protoc's behavior
        if (originalName.startsWith("_")) {
            return originalName.substring(1);
        }
        return originalName;
    }

    /**
     * Get the original field name including any leading underscores
     * that might be present in the .proto file definition
     */
    public String getOriginalName() {
        return descriptor.getName();
    }

    public String getFullName() {
        return descriptor.getFullName();
    }

    public Type getType() {
        return mapType(descriptor.getJavaType());
    }

    public @NotNull Message getMessageType() {
        Validate.validState(getType() == Type.MESSAGE);
        return new Message(descriptor.getMessageType());
    }

    public @NotNull Enum getEnumType() {
        Validate.validState(getType() == Type.ENUM);
        return new Enum(descriptor.getEnumType());
    }

    public Message getContainingMessage() {
        return new Message(descriptor.getContainingType());
    }

    public final boolean isNullable() {
        return isOptional() || (isUnfolded() && getUnfoldedField().isNullable()) || descriptor.getContainingOneof() != null;
    }

    public final boolean isList() {
        return isRepeated();
    }

    public final boolean isMap() {
        return getType() == Type.MESSAGE
                && getMessageType().isMap();
    }

    public final boolean isUnfolded() {
        return getType() == Type.MESSAGE && getMessageType().isUnfolded();
    }

    public final boolean isUnused() {
        return getUnusedFieldOption().orElse(false);
    }

    public final Field getUnfoldedField() {
        Validate.validState(isUnfolded());
        return getMessageType().getFields().get(0);
    }

    /**
     * Returns the field name to be used in generated code,
     * adapting to protoc's behavior of removing leading underscores.
     *
     * @return The field name to be used in generated code
     */
    public final String getGeneratedName() {
        String overriddenName = getOverriddenNameOption().orElseGet(this::getOriginalName);

        // If the name starts with underscore, remove it to match protoc's behavior
        if (overriddenName.startsWith("_")) {
            return overriddenName.substring(1);
        }
        return overriddenName;
    }

    /**
     * Determines if the original field name in protobuf started with an underscore
     *
     * @return true if the original field name started with underscore
     */
    public final boolean hadLeadingUnderscore() {
        return getOriginalName().startsWith("_");
    }

    /**
     * Returns the getter method name for this field, matching protoc's behavior.
     * For a field originally named "_fieldName", protoc generates "getFieldName".
     *
     * @return The getter method name
     */
    public final String getGetterMethodName() {
        String fieldName = getGeneratedName(); // Already handles underscore removal
        String capitalizedName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return "get" + capitalizedName;
    }

    /**
     * Returns the setter method name for this field, matching protoc's behavior.
     * For a field originally named "_fieldName", protoc generates "setFieldName".
     *
     * @return The setter method name
     */
    public final String getSetterMethodName() {
        String fieldName = getGeneratedName(); // Already handles underscore removal
        String capitalizedName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return "set" + capitalizedName;
    }

    public final RepeatedContainer getRepeatedContainer() {
        return getRepeatedContainerOption().orElse(RepeatedContainer.LIST);
    }

    // -----------

    protected boolean isRepeated() {
        return descriptor.isRepeated();
    }

    protected boolean isOptional() {
        return descriptor.hasOptionalKeyword();
    }

    protected Optional<String> getOverriddenNameOption() {
        return Options.wrapExtension(descriptor.getOptions(), protogen.Options.fieldName);
    }

    protected Optional<RepeatedContainer> getRepeatedContainerOption() {
        return Options.wrapExtension(descriptor.getOptions(), protogen.Options.repeatedContainer)
                .map(RepeatedContainer::fromGrpc);
    }

    protected Optional<Boolean> getUnusedFieldOption() {
        return Options.wrapExtension(descriptor.getOptions(), protogen.Options.unusedField);
    }

    private Type mapType(Descriptors.FieldDescriptor.JavaType javaType) {
        switch (javaType) {
            case INT:
                return org.sudu.protogen.descriptors.Field.Type.INT;
            case DOUBLE:
                return org.sudu.protogen.descriptors.Field.Type.DOUBLE;
            case FLOAT:
                return org.sudu.protogen.descriptors.Field.Type.FLOAT;
            case LONG:
                return org.sudu.protogen.descriptors.Field.Type.LONG;
            case BOOLEAN:
                return org.sudu.protogen.descriptors.Field.Type.BOOLEAN;
            case MESSAGE:
                return org.sudu.protogen.descriptors.Field.Type.MESSAGE;
            case ENUM:
                return org.sudu.protogen.descriptors.Field.Type.ENUM;
            case STRING:
                return org.sudu.protogen.descriptors.Field.Type.STRING;
            case BYTE_STRING:
                return org.sudu.protogen.descriptors.Field.Type.BYTE_STRING;
        }
        throw new AssertionError("Unexpected JavaType: " + javaType);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Field field = (Field) o;
        return Objects.equals(descriptor, field.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }

    public enum Type {
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        BOOLEAN,
        STRING,
        BYTE_STRING,
        ENUM,
        MESSAGE
    }
}