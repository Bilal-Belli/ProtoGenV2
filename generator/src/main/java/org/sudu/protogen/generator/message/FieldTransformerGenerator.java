package org.sudu.protogen.generator.message;

import com.squareup.javapoet.CodeBlock;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.sudu.protogen.generator.type.PrimitiveTypeModel;
import org.sudu.protogen.generator.type.TypeModel;
import org.sudu.protogen.utils.Name;

import java.util.Set;

public class FieldTransformerGenerator {
    private final TypeModel type;
    private final String fieldProtoName;
    private final boolean nullable;

    public FieldTransformerGenerator(@NotNull TypeModel type, @NotNull String fieldProtoName, boolean nullable) {
        this.type = type;
        this.fieldProtoName = Name.toCamelCase(fieldProtoName);
        this.nullable = nullable;
    }

    public CodeBlock toGrpc(@NotNull String builderName, @NotNull String generatedFieldName) {
        return new ToGrpc(builderName, generatedFieldName).generate();
    }

    public CodeBlock fromGrpc(@NotNull String protoParameterName) {
        return new FromGrpc(protoParameterName).process();
    }

    private static final Set<String> PRIMITIVE_TYPE_NAMES = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char"
    );

    private boolean isPrimitiveType(String typeName) {
        // Extract simple name from potentially qualified type name
        String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1).toLowerCase();
        return PRIMITIVE_TYPE_NAMES.contains(simpleName);
    }

    private class ToGrpc {
        private final String builderName;
        private final String generatedFieldName;

        public ToGrpc(@NotNull String builderName, @NotNull String generatedFieldName) {
            this.builderName = builderName;
            this.generatedFieldName = generatedFieldName;
        }

        public CodeBlock generate() {
            CodeBlock toGrpc = type.toGrpcTransformer(CodeBlock.builder().add(generatedFieldName).build());
            CodeBlock generated = CodeBlock.builder()
                    .add("$N.$L($L)", builderName, type.setterMethod(fieldProtoName), toGrpc)
                    .build();
            if (nullable) {
                Validate.validState(!(type instanceof PrimitiveTypeModel)); // primitives can't be nullable, but the transformer isn't responsible for that

                // Check if the type is a primitive type that cannot be compared to null
                String typeName = type.getTypeName().toString();
                if (isPrimitiveType(typeName)) {
                    // Primitive types cannot be compared to null
                    return CodeBlock.builder()
                            .addStatement(generated)
                            .build();
                } else {
                    return CodeBlock.builder()
                            .beginControlFlow("if ($N != null)", generatedFieldName)
                            .addStatement(generated)
                            .endControlFlow()
                            .build();
                }
            }
            if (!builderName.equals("")) {
                // make it a statement (add semicolon)
                return CodeBlock.builder().addStatement(generated).build();
            }
            return generated;
        }
    }

    private class FromGrpc {
        private final String protoParameterName;

        public FromGrpc(String protoParameterName) {
            this.protoParameterName = protoParameterName;
        }

        public CodeBlock process() {
            CodeBlock from = CodeBlock.builder()
                    .add("$L.$L()", protoParameterName, type.getterMethod(fieldProtoName))
                    .build();
            from = type.fromGrpcTransformer(from);
            from = wrapToHasCheck(from);
            return from;
        }

        private CodeBlock wrapToHasCheck(CodeBlock from) {
            if (nullable) {
                Validate.validState(!(type instanceof PrimitiveTypeModel)); // primitives can't be nullable, but the transformer isn't responsible for that
                return CodeBlock.builder()
                        .add("$L.has$L() ? $L : null", protoParameterName, fieldProtoName, from)
                        .build();
            }
            return from;
        }
    }
}