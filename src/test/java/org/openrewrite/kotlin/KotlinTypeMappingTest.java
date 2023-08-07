/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.kotlin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.tree.K;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.ExecutionContext.REQUIRE_PRINT_EQUALS_INPUT;
import static org.openrewrite.java.tree.JavaType.GenericTypeVariable.Variance.*;

@SuppressWarnings("ConstantConditions")
public class KotlinTypeMappingTest {
    private static final String goat = StringUtils.readFully(KotlinTypeMappingTest.class.getResourceAsStream("/KotlinTypeGoat.kt"));

    private static final J.ClassDeclaration goatClassDeclaration;

    static {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        ctx.putMessage(REQUIRE_PRINT_EQUALS_INPUT, false);
        //noinspection OptionalGetWithoutIsPresent
        goatClassDeclaration = requireNonNull(((K.CompilationUnit) KotlinParser.builder()
                .logCompilationWarningsAndErrors(true)
                .build()
                .parse(ctx, goat)
                .findFirst()
                .get())
                .getClasses()
                .get(0)
        );
    }

    private static final JavaType.Parameterized goatType =
            requireNonNull(TypeUtils.asParameterized(goatClassDeclaration.getType()));

    public JavaType.Method methodType(String methodName) {
        JavaType.Method type = goatType.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected to find matching method named " + methodName));
        assertThat(type.getDeclaringType().toString()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat");
        return type;
    }

    public J.VariableDeclarations getField(String fieldName) {
        return goatClassDeclaration.getBody().getStatements().stream()
                .filter(s -> s instanceof J.VariableDeclarations)
                .map(J.VariableDeclarations.class::cast)
                .filter(mv -> mv.getVariables().stream().anyMatch(v -> v.getSimpleName().equals(fieldName)))
                .findFirst()
                .orElse(null);
    }

    public JavaType firstMethodParameter(String methodName) {
        return methodType(methodName).getParameterTypes().get(0);
    }

    @Test
    void extendsKotlinAny() {
        assertThat(goatType.getSupertype().getFullyQualifiedName()).isEqualTo("kotlin.Any");
    }

    @Test
    void fieldType() {
        J.VariableDeclarations.NamedVariable variable = getField("field").getVariables().get(0);
        J.Identifier id = variable.getName();
        assertThat(variable.getType()).isEqualTo(id.getType());
        assertThat(id.getFieldType()).isInstanceOf(JavaType.Variable.class);
        assertThat(id.getFieldType().toString()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat{name=field,type=kotlin.Int}");
        assertThat(id.getType()).isInstanceOf(JavaType.Class.class);
        assertThat(id.getType().toString()).isEqualTo("kotlin.Int");
    }

    @Test
    void kotlinAnyHasNoSuperType() {
        assertThat(goatType.getSupertype().getSupertype()).isNull();
    }

    @Test
    void className() {
        JavaType.Class clazz = (JavaType.Class) this.firstMethodParameter("clazz");
        assertThat(TypeUtils.asFullyQualified(clazz).getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.C");
    }

    @Test
    void interfacesContainImplicitAbstractFlag() {
        JavaType.Class clazz = (JavaType.Class) firstMethodParameter("clazz");
        JavaType.Method methodType = methodType("clazz");
        assertThat(clazz.getFlags()).contains(Flag.Abstract);
        assertThat(methodType.getFlags()).contains(Flag.Abstract);
    }

    @Test
    void constructor() {
        JavaType.Method ctor = methodType("<constructor>");
        assertThat(ctor.getDeclaringType().getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat");
    }

    @Test
    void parameterized() {
        JavaType.Parameterized parameterized = (JavaType.Parameterized) firstMethodParameter("parameterized");
        assertThat(parameterized.getType().getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.PT");
        assertThat(TypeUtils.asFullyQualified(parameterized.getTypeParameters().get(0)).getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.C");
    }

    @Test
    void primitive() {
        JavaType.Class kotlinBasicType = (JavaType.Class) firstMethodParameter("primitive");
        assertThat(kotlinBasicType.getFullyQualifiedName()).isEqualTo("kotlin.Int");
    }

    @Test
    void generic() {
        JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) TypeUtils.asParameterized(firstMethodParameter("generic")).getTypeParameters().get(0);
        assertThat(generic.getName()).isEqualTo("");
        assertThat(generic.getVariance()).isEqualTo(COVARIANT);
        assertThat(TypeUtils.asFullyQualified(generic.getBounds().get(0)).getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.C");
    }

    @Test
    void genericContravariant() {
        JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) TypeUtils.asParameterized(firstMethodParameter("genericContravariant")).getTypeParameters().get(0);
        assertThat(generic.getName()).isEqualTo("");
        assertThat(generic.getVariance()).isEqualTo(CONTRAVARIANT);
        assertThat(TypeUtils.asFullyQualified(generic.getBounds().get(0)).getFullyQualifiedName()).
                isEqualTo("org.openrewrite.kotlin.C");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    void genericMultipleBounds() {
        List<JavaType> typeParameters = goatType.getTypeParameters();
        JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) typeParameters.get(typeParameters.size() - 1);
        assertThat(generic.getName()).isEqualTo("S");
        assertThat(generic.getVariance()).isEqualTo(COVARIANT);
        assertThat(TypeUtils.asFullyQualified(generic.getBounds().get(0)).getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.PT");
        assertThat(TypeUtils.asFullyQualified(generic.getBounds().get(1)).getFullyQualifiedName()).
                isEqualTo("org.openrewrite.kotlin.C");
    }

    @Test
    void genericUnbounded() {
        JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) TypeUtils.asParameterized(firstMethodParameter("genericUnbounded")).getTypeParameters().get(0);
        assertThat(generic.getName()).isEqualTo("U");
        assertThat(generic.getVariance()).isEqualTo(INVARIANT);
        assertThat(generic.getBounds()).isEmpty();
    }

    @Disabled
    @Test
    void genericRecursive() {
        JavaType.Parameterized param = (JavaType.Parameterized) firstMethodParameter("genericRecursive");
        JavaType typeParam = param.getTypeParameters().get(0);
        JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) typeParam;
        assertThat(generic.getName()).isEqualTo("");
        assertThat(generic.getVariance()).isEqualTo(COVARIANT);
        assertThat(TypeUtils.asParameterized(generic.getBounds().get(0))).isNotNull();

        JavaType.GenericTypeVariable elemType = (JavaType.GenericTypeVariable) TypeUtils.asParameterized(generic.getBounds().get(0)).getTypeParameters().get(0);
        assertThat(elemType.getName()).isEqualTo("U");
        assertThat(elemType.getVariance()).isEqualTo(COVARIANT);
        assertThat(elemType.getBounds()).hasSize(1);
    }

    @Test
    void innerClass() {
        JavaType.FullyQualified clazz = TypeUtils.asFullyQualified(firstMethodParameter("inner"));
        assertThat(clazz.getFullyQualifiedName()).isEqualTo("org.openrewrite.kotlin.C$Inner");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    void inheritedJavaTypeGoat() {
        JavaType.Parameterized clazz = (JavaType.Parameterized) firstMethodParameter("InheritedKotlinTypeGoat");
        assertThat(clazz.getTypeParameters().get(0).toString()).isEqualTo("Generic{T}");
        assertThat(clazz.getTypeParameters().get(1).toString()).isEqualTo("Generic{U extends org.openrewrite.kotlin.PT<Generic{U}> & org.openrewrite.kotlin.C}");
        assertThat(clazz.toString()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat$InheritedKotlinTypeGoat<Generic{T}, Generic{U extends org.openrewrite.kotlin.PT<Generic{U}> & org.openrewrite.kotlin.C}>");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    void genericIntersectionType() {
        JavaType.GenericTypeVariable clazz = (JavaType.GenericTypeVariable) firstMethodParameter("genericIntersection");
        assertThat(clazz.getBounds().get(0).toString()).isEqualTo("org.openrewrite.java.JavaTypeGoat$TypeA");
        assertThat(clazz.getBounds().get(1).toString()).isEqualTo("org.openrewrite.java.PT<Generic{U extends org.openrewrite.java.JavaTypeGoat$TypeA & org.openrewrite.java.C}>");
        assertThat(clazz.getBounds().get(2).toString()).isEqualTo("org.openrewrite.java.C");
        assertThat(clazz.toString()).isEqualTo("Generic{U extends org.openrewrite.java.JavaTypeGoat$TypeA & org.openrewrite.java.PT<Generic{U}> & org.openrewrite.java.C}");
    }

    @Test
    void enumTypeA() {
        JavaType.Class clazz = (JavaType.Class) firstMethodParameter("enumTypeA");
        JavaType.Method type = clazz.getMethods().stream()
                .filter(m -> "<constructor>".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No constructor found"));
        assertThat(type.toString()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeA{name=<constructor>,return=org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeA,parameters=[]}");

        JavaType.FullyQualified supertype = clazz.getSupertype();
        assertThat(supertype).isNotNull();
        assertThat(supertype.toString()).isEqualTo("kotlin.Enum<org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeA>");
    }

    @Test
    void enumTypeB() {
        JavaType.Class clazz = (JavaType.Class) firstMethodParameter("enumTypeB");
        JavaType.Method type = clazz.getMethods().stream()
                .filter(m -> "<constructor>".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No constructor found"));
        assertThat(type.toString()).isEqualTo("org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeB{name=<constructor>,return=org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeB,parameters=[org.openrewrite.kotlin.KotlinTypeGoat$TypeA]}");

        JavaType.FullyQualified supertype = clazz.getSupertype();
        assertThat(supertype).isNotNull();
        assertThat(supertype.toString()).isEqualTo("kotlin.Enum<org.openrewrite.kotlin.KotlinTypeGoat$EnumTypeB>");
    }

    @Test
    void ignoreSourceRetentionAnnotations() {
        JavaType.Parameterized goat = goatType;
        assertThat(goat.getAnnotations()).hasSize(1);
        assertThat(goat.getAnnotations().get(0).getClassName()).isEqualTo("AnnotationWithRuntimeRetention");

        JavaType.Method clazzMethod = methodType("clazz");
        assertThat(clazzMethod.getAnnotations()).hasSize(1);
        assertThat(clazzMethod.getAnnotations().get(0).getClassName()).isEqualTo("AnnotationWithRuntimeRetention");
    }

    @Disabled("Requires parsing intersection types")
    @Test
    void recursiveIntersection() {
        JavaType.GenericTypeVariable clazz = TypeUtils.asGeneric(firstMethodParameter("recursiveIntersection"));
        assertThat(clazz.toString()).isEqualTo("Generic{U extends org.openrewrite.java.JavaTypeGoat$Extension<Generic{U}> & org.openrewrite.java.Intersection<Generic{U}>}");
    }

    @Test
    void javaLangObject() {
        // These assertions are all based on the JavaTypeMapper.
        JavaType.Class c = (JavaType.Class) firstMethodParameter("javaType");
        assertThat(c.getFullyQualifiedName()).isEqualTo("java.lang.Object");
        assertThat(c.getSupertype()).isNull();
        assertThat(c.getMethods().size()).isEqualTo(12);

        // Assert generic type parameters have the correct type bounds.
        JavaType.Method method = c.getMethods().stream().filter(it -> "getClass".equals(it.getName())).findFirst().orElse(null);
        assertThat(method).isNotNull();
        assertThat(method.toString()).isEqualTo("java.lang.Object{name=getClass,return=java.lang.Class<Generic{?}>,parameters=[]}");

        JavaType.Parameterized returnType = (JavaType.Parameterized) method.getReturnType();
        // Assert the type of the parameterized type contains the type parameter from the source.
        assertThat(returnType.getType().getTypeParameters().get(0).toString()).isEqualTo("Generic{T}");
    }
}
