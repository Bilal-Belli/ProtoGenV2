package org.sudu.protogen.generator.field;

import com.squareup.javapoet.FieldSpec;
import org.jetbrains.annotations.NotNull;
import org.sudu.protogen.descriptors.Field;
import org.sudu.protogen.generator.DescriptorGenerator;
import org.sudu.protogen.generator.GenerationContext;
import org.sudu.protogen.generator.type.TypeModel;
import org.sudu.protogen.utils.Poem;

import java.util.HashSet;
import java.util.Set;

public class FieldGenerator implements DescriptorGenerator<Field, FieldProcessingResult> {

    private final GenerationContext context;

    public FieldGenerator(GenerationContext context) {
        this.context = context;
    }

    @NotNull
    public FieldProcessingResult generate(Field field) {
        if (field.isUnused()) {
            return FieldProcessingResult.empty(field);
        }

        TypeModel type = context.typeManager().processType(field);

        String identifier2 = field.getOriginalName(); // get original name of the attribute

        String rawName = field.getContainingMessage().getName();
        String messageName = rawName.startsWith("Grpc") ? rawName.substring(4) : rawName; // to check visibility of the original class name

        boolean resultNeedGetter = false;
        try {
            Set<String> projectPaths = new HashSet<>();
            projectPaths.add("C:\\Users\\bilal.belli\\eclipse-workspace\\myProject\\src\\main\\java");
            // Add others if necessary
            resultNeedGetter = AttributeVisibilityAnalyzerMultiProject.needGetter(messageName, identifier2, projectPaths);
        } catch (Exception e) {
            e.printStackTrace();
        }

        FieldSpec.Builder fieldSpecBuilder;
        field.setNeedGetterInOriginalClass(resultNeedGetter);
        if (resultNeedGetter){
            String identifier4 = field.getGetterMethodName();
            fieldSpecBuilder = FieldSpec.builder(type.getTypeName(), identifier4);
        } else {
            fieldSpecBuilder = FieldSpec.builder(type.getTypeName(), identifier2);
        }

        boolean isNullable = field.isNullable();

        if (!type.isPrimitiveOrVoid()) {
            if (field.getContainingMessage().getContainingFile().doUseNullabilityAnnotation(isNullable)) {
                Poem.attachNullabilityAnnotations(fieldSpecBuilder, context, isNullable);
            }
        }

        return new FieldProcessingResult(field, fieldSpecBuilder.build(), type, isNullable);
    }
}
