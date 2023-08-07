/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.kotlin.format;


import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.style.BlankLinesStyle;
import org.openrewrite.kotlin.tree.K;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class BlankLinesVisitor<P> extends KotlinIsoVisitor<P> {
    @Nullable
    private final Tree stopAfter;

    private final BlankLinesStyle style;

    // Unconfigurable default formatting in IntelliJ's Kotlin formatting.
    private static final int keepMaximumBlankLines_BetweenHeaderAndPackage = 2;
    // private static final int minimumBlankLines_BeforePackageStatement = 0;
    private static final int minimumBlankLines_AfterPackageStatement = 1;
    private static final int minimumBlankLines_BeforeImports = 1;
    private static final int minimumBlankLines_AfterImports = 1;
    private static final int minimumBlankLines_AroundClass = 1;
    private static final int minimumBlankLines_BeforeClassEnd = 0;
    private static final int minimumBlankLines_AfterAnonymousClassHeader = 0;
    private static final int minimumBlankLines_AroundFieldInInterface = 0;
    private static final int minimumBlankLines_AroundField = 0;
    private static final int minimumBlankLines_AroundMethodInInterface = 1;
    private static final int minimumBlankLines_AroundMethod = 1;
    private static final int minimumBlankLines_BeforeMethodBody = 0;
    private static final int minimumBlankLines_AroundInitializer = 1;

    public BlankLinesVisitor(BlankLinesStyle style) {
        this(style, null);
    }

    public BlankLinesVisitor(BlankLinesStyle style, @Nullable Tree stopAfter) {
        this.style = style;
        this.stopAfter = stopAfter;
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (getCursor().getNearestMessage("stop") != null) {
            return (J) tree;
        }

        if (tree instanceof K.CompilationUnit) {
            K.CompilationUnit cu = (K.CompilationUnit) requireNonNull(tree);
            if (cu.getPackageDeclaration() != null) {
                J.Package pa =  cu.getPackageDeclaration();
                if (!pa.getPrefix().getComments().isEmpty()) {
                    List<Comment> updatedComments =  ListUtils.mapLast(pa.getPrefix().getComments(), c -> {
                        String suffix = keepMaximumLines(c.getSuffix(), keepMaximumBlankLines_BetweenHeaderAndPackage);
                        suffix = minimumLines(suffix, keepMaximumBlankLines_BetweenHeaderAndPackage);
                        return c.withSuffix(suffix);
                    });
                    cu = cu.withPackageDeclaration(pa.withPrefix(pa.getPrefix().withComments(updatedComments)));
                } else {
                    /*
                     if comments are empty and package is present, leading whitespace is on the compilation unit and
                     should be removed
                     */
                    cu = cu.withPackageDeclaration(pa.withPrefix(Space.EMPTY));
                }
            }

            if (cu.getPackageDeclaration() == null) {
                if (cu.getComments().isEmpty()) {
                /*
                if package decl and comments are null/empty, leading whitespace is on the
                compilation unit and should be removed
                 */
                    cu = cu.withPrefix(Space.EMPTY);
                } else {
                    cu = cu.withComments(ListUtils.mapLast(cu.getComments(), c ->
                            c.withSuffix(minimumLines(c.getSuffix(), minimumBlankLines_BeforeImports))));
                }
            } else {

                cu = cu.getPadding().withImports(ListUtils.mapFirst(cu.getPadding().getImports(), i ->
                        minimumLines(i,  minimumBlankLines_AfterPackageStatement)));
            }
            return super.visit(cu, p);
        }
        return super.visit(tree, p);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        J.ClassDeclaration j = super.visitClassDeclaration(classDecl, p);

        List<JRightPadded<Statement>> statements = j.getBody().getPadding().getStatements();
        J.ClassDeclaration.Kind.Type classKind = j.getKind();
        j = j.withBody(j.getBody().getPadding().withStatements(ListUtils.map(statements, (i, s) -> {
            if (i == 0) {
                if (classKind != J.ClassDeclaration.Kind.Type.Enum) {
                    s = minimumLines(s, style.getMinimum().getAfterClassHeader());
                }
            } else if (statements.get(i - 1).getElement() instanceof J.Block) {
                s = minimumLines(s, minimumBlankLines_AroundInitializer);
            } else if (s.getElement() instanceof J.ClassDeclaration) {
                // Apply `style.getMinimum().getAroundClass()` to inner classes.
                s = minimumLines(s, minimumBlankLines_AroundClass);
            }

            return s;
        })));

        j = j.withBody(j.getBody().withEnd(minimumLines(j.getBody().getEnd(),
                minimumBlankLines_BeforeClassEnd)));

        JavaSourceFile cu = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
        boolean hasImports = !cu.getImports().isEmpty();
        boolean firstClass = j.equals(cu.getClasses().get(0));
        Set<J.ClassDeclaration> classes = new HashSet<>(cu.getClasses());

        j = firstClass ?
                (hasImports ? minimumLines(j, minimumBlankLines_AfterImports) : j) :
                // Apply `style.getMinimum().getAroundClass()` to classes declared in the SourceFile.
                (classes.contains(j)) ? minimumLines(j, minimumBlankLines_AroundClass) : j;

        // style.getKeepMaximum().getInDeclarations() also sets the maximum new lines of class declaration prefixes.
        j = firstClass ?
                (hasImports ? keepMaximumLines(j, Math.max(style.getKeepMaximum().getInDeclarations(), minimumBlankLines_AfterImports)) : j) :
                (classes.contains(j)) ? keepMaximumLines(j, style.getKeepMaximum().getInDeclarations()) : j;

        if (!hasImports && firstClass) {
            if (cu.getPackageDeclaration() == null) {
                if (!j.getPrefix().getWhitespace().isEmpty()) {
                    j = j.withPrefix(j.getPrefix().withWhitespace(""));
                }
            } else {
                j = minimumLines(j, minimumBlankLines_AfterPackageStatement);
            }
        }

        return j;
    }

    @Override
    public J.EnumValue visitEnumValue(J.EnumValue _enum, P p) {
        J.EnumValue e = super.visitEnumValue(_enum, p);
        return keepMaximumLines(e, style.getKeepMaximum().getInDeclarations());
    }

    @Override
    public J.Import visitImport(J.Import import_, P p) {
        J.Import i = super.visitImport(import_, p);
        JavaSourceFile cu = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
        if (i.equals(cu.getImports().get(0)) && cu.getPackageDeclaration() == null && cu.getPrefix().equals(Space.EMPTY)) {
            i = i.withPrefix(i.getPrefix().withWhitespace(""));
        }
        return i;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
        J.MethodDeclaration j = super.visitMethodDeclaration(method, p);
        if (j.getBody() != null) {
            if (j.getBody().getStatements().isEmpty()) {
                Space end = minimumLines(j.getBody().getEnd(),
                        minimumBlankLines_BeforeMethodBody);
                if (end.getIndent().isEmpty() && minimumBlankLines_BeforeMethodBody > 0) {
                    end = end.withWhitespace(end.getWhitespace() + method.getPrefix().getIndent());
                }
                j = j.withBody(j.getBody().withEnd(end));
            } else {
                j = j.withBody(j.getBody().withStatements(ListUtils.mapFirst(j.getBody().getStatements(), s ->
                        minimumLines(s, minimumBlankLines_BeforeMethodBody))));
            }
        }

        return j;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
        J.NewClass j = super.visitNewClass(newClass, p);
        if (j.getBody() != null) {
            j = j.withBody(j.getBody().withStatements(ListUtils.mapFirst(j.getBody().getStatements(), s ->
                    minimumLines(s, minimumBlankLines_AfterAnonymousClassHeader))));
        }
        return j;
    }

    @Override
    public Statement visitStatement(Statement statement, P p) {
        Statement j = super.visitStatement(statement, p);
        Iterator<Object> cursorPath = getCursor().getParentOrThrow().getPath(J.class::isInstance);
        Object parentTree = cursorPath.next();
        if (cursorPath.hasNext()) {
            Object grandparentTree = cursorPath.next();
            if (grandparentTree instanceof J.ClassDeclaration && parentTree instanceof J.Block) {
                J.Block block = (J.Block) parentTree;
                J.ClassDeclaration classDecl = (J.ClassDeclaration) grandparentTree;

                int declMax = style.getKeepMaximum().getInDeclarations();

                // don't adjust the first statement in a block
                if (!block.getStatements().isEmpty() && !block.getStatements().iterator().next().isScope(j)) {
                    if (j instanceof J.VariableDeclarations) {
                        if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                            declMax = Math.max(declMax, minimumBlankLines_AroundFieldInInterface);
                            j = minimumLines(j, minimumBlankLines_AroundFieldInInterface);
                        } else {
                            declMax = Math.max(declMax, minimumBlankLines_AroundField);
                            j = minimumLines(j, minimumBlankLines_AroundField);
                        }
                    } else if (j instanceof J.MethodDeclaration) {
                        if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                            declMax = Math.max(declMax, minimumBlankLines_AroundMethodInInterface);
                            j = minimumLines(j, minimumBlankLines_AroundMethodInInterface);
                        } else {
                            declMax = Math.max(declMax, minimumBlankLines_AroundMethod);
                            j = minimumLines(j, minimumBlankLines_AroundMethod);
                        }
                    } else if (j instanceof J.Block) {
                        declMax = Math.max(declMax, minimumBlankLines_AroundInitializer);
                        j = minimumLines(j, minimumBlankLines_AroundInitializer);
                    } else if (j instanceof J.ClassDeclaration) {
                        declMax = Math.max(declMax, minimumBlankLines_AroundClass);
                        j = minimumLines(j, minimumBlankLines_AroundClass);
                    }
                }

                j = keepMaximumLines(j, declMax);
            } else {
                return keepMaximumLines(j, style.getKeepMaximum().getInCode());
            }
        }
        return j;
    }

    @Override
    public J.Block visitBlock(J.Block block, P p) {
        J.Block j = super.visitBlock(block, p);
        j = j.withEnd(keepMaximumLines(j.getEnd(), style.getKeepMaximum().getBeforeEndOfBlock()));
        return j;
    }

    private <J2 extends J> J2 keepMaximumLines(J2 tree, int max) {
        return tree.withPrefix(keepMaximumLines(tree.getPrefix(), max));
    }

    private Space keepMaximumLines(Space prefix, int max) {
        return prefix.withWhitespace(keepMaximumLines(prefix.getWhitespace(), max));
    }

    private String keepMaximumLines(String whitespace, int max) {
        long blankLines = getNewLineCount(whitespace) - 1;
        if (blankLines > max) {
            int startWhitespaceAtIndex = 0;
            for (int i = 0; i < blankLines - max + 1; i++, startWhitespaceAtIndex++) {
                startWhitespaceAtIndex = whitespace.indexOf('\n', startWhitespaceAtIndex);
            }
            startWhitespaceAtIndex--;
            return whitespace.substring(startWhitespaceAtIndex);
        }
        return whitespace;
    }

    private <J2 extends J> JRightPadded<J2> minimumLines(JRightPadded<J2> tree, int min) {
        return tree.withElement(minimumLines(tree.getElement(), min));
    }

    private <J2 extends J> J2 minimumLines(J2 tree, int min) {
        return tree.withPrefix(minimumLines(tree.getPrefix(), min));
    }

    private Space minimumLines(Space prefix, int min) {
        if (min == 0) {
            return prefix;
        }
        if (prefix.getComments().isEmpty() ||
                prefix.getWhitespace().contains("\n") ||
                prefix.getComments().get(0) instanceof Javadoc ||
                (prefix.getComments().get(0).isMultiline() && prefix.getComments().get(0)
                        .printComment(getCursor()).contains("\n"))) {
            return prefix.withWhitespace(minimumLines(prefix.getWhitespace(), min));
        }

        // the first comment is a trailing comment on the previous line
        return prefix.withComments(ListUtils.map(prefix.getComments(), (i, c) -> i == 0 ?
                c.withSuffix(minimumLines(c.getSuffix(), min)) : c));
    }

    private String minimumLines(String whitespace, int min) {
        if (min == 0) {
            return whitespace;
        }
        String minWhitespace = whitespace;

        for (int i = 0; i < min - getNewLineCount(whitespace) + 1; i++) {
            //noinspection StringConcatenationInLoop
            minWhitespace = "\n" + minWhitespace;
        }
        return minWhitespace;
    }

    private static int getNewLineCount(String whitespace) {
        int newLineCount = 0;
        for (char c : whitespace.toCharArray()) {
            if (c == '\n') {
                newLineCount++;
            }
        }
        return newLineCount;
    }

    @Nullable
    @Override
    public J postVisit(J tree, P p) {
        if (stopAfter != null && stopAfter.isScope(tree)) {
            getCursor().putMessageOnFirstEnclosing(JavaSourceFile.class, "stop", true);
        }
        return super.postVisit(tree, p);
    }
}

