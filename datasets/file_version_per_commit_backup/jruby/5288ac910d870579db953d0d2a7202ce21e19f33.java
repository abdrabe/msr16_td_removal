package org.jruby.anno;

import org.jruby.CompatVersion;
import org.jruby.util.CodegenUtils;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes({"org.jruby.anno.JRubyMethod", "org.jruby.anno.JRubyClass"})
public class AnnotationBinder extends AbstractProcessor {

    public static final String POPULATOR_SUFFIX = "$POPULATOR";
    private static final Logger LOG = LoggerFactory.getLogger("AnnotationBinder");
    private final List<CharSequence> classNames = new ArrayList<CharSequence>();
    private PrintStream out;
    private static final boolean DEBUG = false;

    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment) {
        for (TypeElement typeDecl : typeElements) {
            processType(typeDecl);
        }
        try {
            FileWriter fw = new FileWriter("src_gen/annotated_classes.txt");
            for (CharSequence name : classNames) {
                fw.write(name.toString());
                fw.write('\n');
            }
            fw.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public void processType(TypeElement cd) {
        try {
            String qualifiedName = cd.getQualifiedName().toString().replace('.', '$');

            // skip anything not related to jruby
            if (!qualifiedName.contains("org$jruby")) {
                return;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
            out = new PrintStream(bytes);

            // start a new populator
            out.println("/* THIS FILE IS GENERATED. DO NOT EDIT */");
            out.println("package org.jruby.gen;");

            out.println("import org.jruby.Ruby;");
            out.println("import org.jruby.RubyModule;");
            out.println("import org.jruby.RubyClass;");
            out.println("import org.jruby.CompatVersion;");
            out.println("import org.jruby.anno.TypePopulator;");
            out.println("import org.jruby.internal.runtime.methods.CallConfiguration;");
            out.println("import org.jruby.internal.runtime.methods.JavaMethod;");
            out.println("import org.jruby.internal.runtime.methods.DynamicMethod;");
            out.println("import org.jruby.runtime.Arity;");
            out.println("import org.jruby.runtime.Visibility;");
            out.println("import org.jruby.compiler.ASTInspector;");
            out.println("import java.util.Arrays;");
            out.println("import java.util.List;");

            out.println("public class " + qualifiedName + POPULATOR_SUFFIX + " extends TypePopulator {");
            out.println("    public void populate(RubyModule cls, Class clazz) {");
            if (DEBUG) {
                out.println("        System.out.println(\"Using pregenerated populator: \" + \"" + qualifiedName + POPULATOR_SUFFIX + "\");");
            }

            // scan for meta, compat, etc to reduce findbugs complaints about "dead assignments"
            boolean hasMeta = false;
            boolean hasModule = false;
            boolean hasCompat = false;
            for (ExecutableElement method : ElementFilter.methodsIn(cd.getEnclosedElements())) {
                JRubyMethod anno = method.getAnnotation(JRubyMethod.class);
                if (anno == null) {
                    continue;
                }
                hasMeta |= anno.meta();
                hasModule |= anno.module();
                hasCompat |= anno.compat() != CompatVersion.BOTH;
            }

            out.println("        JavaMethod javaMethod;");
            out.println("        DynamicMethod moduleMethod;");
            if (hasMeta || hasModule) out.println("        RubyClass singletonClass = cls.getSingletonClass();");
            if (hasCompat)
                out.println("        CompatVersion compatVersion = cls.getRuntime().getInstanceConfig().getCompatVersion();");
            out.println("        Ruby runtime = cls.getRuntime();");

            Map<CharSequence, List<ExecutableElement>> annotatedMethods = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> staticAnnotatedMethods = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> annotatedMethods1_8 = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> staticAnnotatedMethods1_8 = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> annotatedMethods1_9 = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> staticAnnotatedMethods1_9 = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> annotatedMethods2_0 = new HashMap<CharSequence, List<ExecutableElement>>();
            Map<CharSequence, List<ExecutableElement>> staticAnnotatedMethods2_0 = new HashMap<CharSequence, List<ExecutableElement>>();

            Set<CharSequence> frameAwareMethods = new HashSet<CharSequence>();
            Set<CharSequence> scopeAwareMethods = new HashSet<CharSequence>();

            int methodCount = 0;
            for (ExecutableElement method : ElementFilter.methodsIn(cd.getEnclosedElements())) {
                JRubyMethod anno = method.getAnnotation(JRubyMethod.class);
                if (anno == null) {
                    continue;
                }
                methodCount++;

                // warn if the method raises any exceptions (JRUBY-4494)
                if (method.getThrownTypes().size() != 0) {
                    System.err.print("Method " + cd.toString() + "." + method.toString() + " should not throw exceptions: ");
                    boolean comma = false;
                    for (TypeMirror thrownType : method.getThrownTypes()) {
                        if (comma) System.err.print(", ");
                        System.err.print(thrownType);
                        comma = true;
                    }
                    System.err.print("\n");
                }

                CharSequence name = anno.name().length == 0 ? method.getSimpleName() : anno.name()[0];

                List<ExecutableElement> methodDescs;
                Map<CharSequence, List<ExecutableElement>> methodsHash = null;
                if (method.getModifiers().contains(Modifier.STATIC)) {
                    if (anno.compat() == CompatVersion.RUBY1_8) {
                        methodsHash = staticAnnotatedMethods1_8;
                    } else if (anno.compat() == CompatVersion.RUBY1_9) {
                        methodsHash = staticAnnotatedMethods1_9;
                    } else if (anno.compat() == CompatVersion.RUBY2_0) {
                        methodsHash = staticAnnotatedMethods2_0;
                    } else {
                        methodsHash = staticAnnotatedMethods;
                    }
                } else {
                    if (anno.compat() == CompatVersion.RUBY1_8) {
                        methodsHash = annotatedMethods1_8;
                    } else if (anno.compat() == CompatVersion.RUBY1_9) {
                        methodsHash = annotatedMethods1_9;
                    } else if (anno.compat() == CompatVersion.RUBY2_0) {
                        methodsHash = annotatedMethods2_0;
                    } else {
                        methodsHash = annotatedMethods;
                    }
                }

                methodDescs = methodsHash.get(name);
                if (methodDescs == null) {
                    methodDescs = new ArrayList<ExecutableElement>();
                    methodsHash.put(name, methodDescs);
                }

                methodDescs.add(method);

                // check for frame field reads or writes
                boolean frame = false;
                boolean scope = false;
                if (anno.frame()) {
                    if (DEBUG)
                        LOG.debug("Method has frame = true: {}:{}", methodDescs.get(0).getEnclosingElement(), methodDescs);
                    frame = true;
                }
                if (anno.reads() != null) for (FrameField read : anno.reads()) {
                    switch (read) {
                        case BACKREF:
                        case LASTLINE:
                            if (DEBUG)
                                LOG.debug("Method reads scope field {}: {}: {}", read, methodDescs.get(0).getEnclosingElement(), methodDescs);
                            scope = true;
                            break;
                        default:
                            if (DEBUG)
                                LOG.debug("Method reads frame field {}: {}: {}", read, methodDescs.get(0).getEnclosingElement(), methodDescs);
                            ;
                            frame = true;
                    }
                }
                if (anno.writes() != null) for (FrameField write : anno.writes()) {
                    switch (write) {
                        case BACKREF:
                        case LASTLINE:
                            if (DEBUG)
                                LOG.debug("Method writes scope field {}: {}: {}", write, methodDescs.get(0).getEnclosingElement(), methodDescs);
                            scope = true;
                            break;
                        default:
                            if (DEBUG)
                                LOG.debug("Method writes frame field {}: {}: {}", write, methodDescs.get(0).getEnclosingElement(), methodDescs);
                            frame = true;
                    }
                }
                if (frame) frameAwareMethods.addAll(Arrays.asList(anno.name()));
                if (scope) scopeAwareMethods.addAll(Arrays.asList(anno.name()));
            }

            if (methodCount == 0) {
                // no annotated methods found, skip
                return;
            }

            classNames.add(getActualQualifiedName(cd));

            processMethodDeclarations(staticAnnotatedMethods);
            for (Map.Entry<CharSequence, List<ExecutableElement>> entry : staticAnnotatedMethods.entrySet()) {
                ExecutableElement decl = entry.getValue().get(0);
                if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
            }

            if (!staticAnnotatedMethods1_8.isEmpty()) {
                out.println("        if (compatVersion == CompatVersion.RUBY1_8 || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(staticAnnotatedMethods1_8);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : staticAnnotatedMethods1_8.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            if (!staticAnnotatedMethods1_9.isEmpty()) {
                out.println("        if (compatVersion.is1_9() || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(staticAnnotatedMethods1_9);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : staticAnnotatedMethods1_9.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            if (!staticAnnotatedMethods2_0.isEmpty()) {
                out.println("        if (compatVersion.is2_0() || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(staticAnnotatedMethods2_0);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : staticAnnotatedMethods2_0.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            processMethodDeclarations(annotatedMethods);
            for (Map.Entry<CharSequence, List<ExecutableElement>> entry : annotatedMethods.entrySet()) {
                ExecutableElement decl = entry.getValue().get(0);
                if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
            }

            if (!annotatedMethods1_8.isEmpty()) {
                out.println("        if (compatVersion == CompatVersion.RUBY1_8 || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(annotatedMethods1_8);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : annotatedMethods1_8.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            if (!annotatedMethods1_9.isEmpty()) {
                out.println("        if (compatVersion.is1_9() || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(annotatedMethods1_9);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : annotatedMethods1_9.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            if (!annotatedMethods2_0.isEmpty()) {
                out.println("        if (compatVersion.is2_0() || compatVersion == CompatVersion.BOTH) {");
                processMethodDeclarations(annotatedMethods2_0);
                for (Map.Entry<CharSequence, List<ExecutableElement>> entry : annotatedMethods2_0.entrySet()) {
                    ExecutableElement decl = entry.getValue().get(0);
                    if (!decl.getAnnotation(JRubyMethod.class).omit()) addCoreMethodMapping(entry.getKey(), decl, out);
                }
                out.println("        }");
            }

            out.println("    }");

            // write out a static initializer for frame names, so it only fires once
            out.println("    static {");
            if (!frameAwareMethods.isEmpty()) {
                StringBuffer frameMethodsString = new StringBuffer();
                boolean first = true;
                for (CharSequence name : frameAwareMethods) {
                    if (!first) frameMethodsString.append(',');
                    first = false;
                    frameMethodsString.append('"').append(name).append('"');
                }
                out.println("        ASTInspector.addFrameAwareMethods(" + frameMethodsString + ");");
            }
            if (!scopeAwareMethods.isEmpty()) {
                StringBuffer scopeMethodsString = new StringBuffer();
                boolean first = true;
                for (CharSequence name : scopeAwareMethods) {
                    if (!first) scopeMethodsString.append(',');
                    first = false;
                    scopeMethodsString.append('"').append(name).append('"');
                }
                out.println("        ASTInspector.addScopeAwareMethods(" + scopeMethodsString + ");");
            }
            out.println("     }");

            out.println("}");
            out.close();
            out = null;

            FileOutputStream fos = new FileOutputStream("src_gen/" + qualifiedName + POPULATOR_SUFFIX + ".java");
            fos.write(bytes.toByteArray());
            fos.close();
        } catch (IOException ioe) {
            LOG.error("FAILED TO GENERATE:", ioe);
            System.exit(1);
        }
    }

    public void processMethodDeclarations(Map<CharSequence, List<ExecutableElement>> declarations) {
        for (Map.Entry<CharSequence, List<ExecutableElement>> entry : declarations.entrySet()) {
            List<ExecutableElement> list = entry.getValue();

            if (list.size() == 1) {
                // single method, use normal logic
                processMethodDeclaration(list.get(0));
            } else {
                // multimethod, new logic
                processMethodDeclarationMulti(list.get(0));
            }
        }
    }

    public void processMethodDeclaration(ExecutableElement method) {
        JRubyMethod anno = method.getAnnotation(JRubyMethod.class);
        if (anno != null && out != null) {
            boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
            CharSequence qualifiedName = getActualQualifiedName(method.getEnclosingElement());

            boolean hasContext = false;
            boolean hasBlock = false;

            StringBuffer buffer = new StringBuffer();
            boolean first = true;
            for (VariableElement parameter : method.getParameters()) {
                if (!first) buffer.append(", ");
                first = false;
                buffer.append(parameter.asType().toString());
                buffer.append(".class");
                hasContext |= parameter.asType().toString().equals("org.jruby.runtime.ThreadContext");
                hasBlock |= parameter.asType().toString().equals("org.jruby.runtime.Block");
            }

            int actualRequired = calculateActualRequired(method, method.getParameters().size(), anno.optional(), anno.rest(), isStatic, hasContext, hasBlock);

            String annotatedBindingName = CodegenUtils.getAnnotatedBindingClassName(
                    method.getSimpleName(),
                    qualifiedName,
                    isStatic,
                    actualRequired,
                    anno.optional(),
                    false,
                    anno.frame());
            String implClass = anno.meta() ? "singletonClass" : "cls";

            out.println("        javaMethod = new " + annotatedBindingName + "(" + implClass + ", Visibility." + anno.visibility() + ");");
            out.println("        populateMethod(javaMethod, " +
                    +getArityValue(anno, actualRequired) + ", \""
                    + method.getSimpleName() + "\", "
                    + isStatic + ", "
                    + "CallConfiguration." + getCallConfigNameByAnno(anno) + ", "
                    + anno.notImplemented() + ", "
                    + method.getEnclosingElement().getSimpleName() + ".class, "
                    + "\"" + method.getSimpleName() + "\", "
                    + method.getReturnType().toString() + ".class, "
                    + "new Class[] {" + buffer.toString() + "});");
            generateMethodAddCalls(method, anno);
        }
    }

    public void processMethodDeclarationMulti(ExecutableElement method) {
        JRubyMethod anno = method.getAnnotation(JRubyMethod.class);
        if (anno != null && out != null) {
            boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
            CharSequence qualifiedName = getActualQualifiedName(method.getEnclosingElement());

            boolean hasContext = false;
            boolean hasBlock = false;

            StringBuffer buffer = new StringBuffer();
            boolean first = true;
            for (VariableElement parameter : method.getParameters()) {
                if (!first) buffer.append(", ");
                first = false;
                buffer.append(parameter.asType().toString());
                buffer.append(".class");
                hasContext |= parameter.asType().toString().equals("org.jruby.runtime.ThreadContext");
                hasBlock |= parameter.asType().toString().equals("org.jruby.runtime.Block");
            }

            int actualRequired = calculateActualRequired(method, method.getParameters().size(), anno.optional(), anno.rest(), isStatic, hasContext, hasBlock);

            String annotatedBindingName = CodegenUtils.getAnnotatedBindingClassName(
                    method.getSimpleName(),
                    qualifiedName,
                    isStatic,
                    actualRequired,
                    anno.optional(),
                    true,
                    anno.frame());
            String implClass = anno.meta() ? "singletonClass" : "cls";

            out.println("        javaMethod = new " + annotatedBindingName + "(" + implClass + ", Visibility." + anno.visibility() + ");");
            out.println("        populateMethod(javaMethod, " +
                    "-1, \"" +
                    method.getSimpleName() + "\", " +
                    isStatic + ", " +
                    "CallConfiguration." + getCallConfigNameByAnno(anno) + ", " +
                    anno.notImplemented() + ", "
                    + method.getEnclosingElement().getSimpleName() + ".class, "
                    + "\"" + method.getSimpleName() + "\", "
                    + method.getReturnType().toString() + ".class, "
                    + "new Class[] {" + buffer.toString() + "});");
            generateMethodAddCalls(method, anno);
        }
    }

    private void addCoreMethodMapping(CharSequence rubyName, ExecutableElement decl, PrintStream out) {
        out.println(new StringBuilder(50)
                .append("        runtime.addBoundMethod(")
                .append('"').append(decl.getEnclosingElement().getSimpleName()).append('"')
                .append(',')
                .append('"').append(decl.getSimpleName()).append('"')
                .append(',')
                .append('"').append(rubyName).append('"')
                .append(");").toString());
    }

    private CharSequence getActualQualifiedName(Element td) {
        // declared type returns the qualified name without $ for inner classes!!!
        CharSequence qualifiedName;
        if (td.getEnclosingElement() != null) {
            // inner class, use $ to delimit
            if (td.getEnclosingElement().getEnclosingElement() != null) {
                qualifiedName = td.getEnclosingElement().getEnclosingElement().getSimpleName() + "$" + td.getEnclosingElement().getSimpleName() + "$" + td.getSimpleName();
            } else {
                qualifiedName = td.getEnclosingElement().getSimpleName() + "$" + td.getSimpleName();
            }
        } else {
            qualifiedName = td.getSimpleName();
        }

        return qualifiedName;
    }

    private int calculateActualRequired(ExecutableElement md, int paramsLength, int optional, boolean rest, boolean isStatic, boolean hasContext, boolean hasBlock) {
        int actualRequired;
        if (optional == 0 && !rest) {
            int args = paramsLength;
            if (args == 0) {
                actualRequired = 0;
            } else {
                if (isStatic) {
                    args--;
                }
                if (hasContext) {
                    args--;
                }
                if (hasBlock) {
                    args--;                        // TODO: confirm expected args are IRubyObject (or similar)
                }
                actualRequired = args;
            }
        } else {
            // optional args, so we have IRubyObject[]
            // TODO: confirm
            int args = paramsLength;
            if (args == 0) {
                actualRequired = 0;
            } else {
                if (isStatic) {
                    args--;
                }
                if (hasContext) {
                    args--;
                }
                if (hasBlock) {
                    args--;                        // minus one more for IRubyObject[]
                }
                args--;

                // TODO: confirm expected args are IRubyObject (or similar)
                actualRequired = args;
            }

            if (actualRequired != 0) {
                throw new RuntimeException("Combining specific args with IRubyObject[] is not yet supported: "
                        + md.getEnclosingElement().getSimpleName() + "." + md.toString());
            }
        }

        return actualRequired;
    }

    public void generateMethodAddCalls(ExecutableElement md, JRubyMethod jrubyMethod) {
        if (jrubyMethod.meta()) {
            defineMethodOnClass("javaMethod", "singletonClass", jrubyMethod, md);
        } else {
            defineMethodOnClass("javaMethod", "cls", jrubyMethod, md);
            if (jrubyMethod.module()) {
                out.println("        moduleMethod = populateModuleMethod(cls, javaMethod);");
                defineMethodOnClass("moduleMethod", "singletonClass", jrubyMethod, md);
            }
        }
        //                }
    }

    private void defineMethodOnClass(String methodVar, String classVar, JRubyMethod jrubyMethod, ExecutableElement md) {
        CharSequence baseName;
        if (jrubyMethod.name().length == 0) {
            baseName = md.getSimpleName();
            out.println("        " + classVar + ".addMethodAtBootTimeOnly(\"" + baseName + "\", " + methodVar + ");");
        } else {
            baseName = jrubyMethod.name()[0];
            for (String name : jrubyMethod.name()) {
                out.println("        " + classVar + ".addMethodAtBootTimeOnly(\"" + name + "\", " + methodVar + ");");
            }
        }

        if (jrubyMethod.alias().length > 0) {
            for (String alias : jrubyMethod.alias()) {
                out.println("        " + classVar + ".defineAlias(\"" + alias + "\", \"" + baseName + "\");");
            }
        }
    }

    public static int getArityValue(JRubyMethod anno, int actualRequired) {
        if (anno.optional() > 0 || anno.rest()) {
            return -(actualRequired + 1);
        }
        return actualRequired;
    }

    public static String getCallConfigNameByAnno(JRubyMethod anno) {
        return getCallConfigName(anno.frame(), anno.scope());
    }

    public static String getCallConfigName(boolean frame, boolean scope) {
        if (frame) {
            if (scope) {
                return "FrameFullScopeFull";
            } else {
                return "FrameFullScopeNone";
            }
        } else if (scope) {
            return "FrameNoneScopeFull";
        } else {
            return "FrameNoneScopeNone";
        }
    }
}
