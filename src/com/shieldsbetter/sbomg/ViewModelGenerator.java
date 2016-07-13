/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.shieldsbetter.sbomg;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 *
 * @author hamptos
 */
public class ViewModelGenerator {
    private static final Template LIST_ITERATOR_TEMPL;
    private static final String LIST_ITERATOR_CODE = ""
            + "      return new Iterator<${elementType}>() {\n"
            + "          private final Iterator<${contextName}Key> "
            + "myBaseIterator = ${baseIterable}.iterator();\n\n"
            + "          @Override\n"
            + "          public boolean hasNext() {\n"
            + "            return myBaseIterator.hasNext();\n"
            + "          }\n\n"
            + "          @Override\n"
            + "          public void remove() {\n"
            + "            throw new UnsupportedOperationException();\n"
            + "          }\n\n"
            + "          @Override\n"
            + "          public ${elementType} next() {\n"
            + "            return myBaseIterator.next().getValue();\n"
            + "          }\n"
            + "        };\n"
            ;
    
    private static final Template RETURN_LIST_METHOD_TEMPL;
    private static final String RETURN_LIST_METHOD_CODE = ""
            + "return new Iterable<${elementType}>() {\n"
            + "    @Override\n"
            + "    public $T iterator() {\n"
            + "      ${iteratorCode}"
            + "    }\n"
            + "  };\n"
            ;
    
    private static final Template REPLACE_LIST_METHOD_TEMPL;
    private static final String REPLACE_LIST_METHOD_CODE = ""
            + "if (${elementsParam} == null) {\n"
            + "  throw new NullPointerException();\n"
            + "}\n\n"
            + "<#if !leafFlag>"
            + "for (${contextName}Key k : my${contextName}List) {\n"
            + "  if (my${contextName}Subscriptions.containsKey(k)) {\n"
            + "    my${contextName}Subscriptions.remove(k).unsubscribe();\n"
            + "  }\n"
            + "}\n\n"
            + "</#if>"
            + "my${contextName}List = new LinkedList<>();\n"
            + "final List<${elementType}> valueList = new LinkedList<>();\n"
            + "for (${elementType} e : ${elementsParam}) {\n"
            + "  valueList.add(e);\n"
            + "  ${contextName}Key k = new ${contextName}Key(e);"
            + "  my${contextName}List.add(k);\n"
            + "<#if !leafFlag>"
            + "  subscribeIfNotNull(k);\n"
            + "</#if>"
            + "}\n\n"
            + "Event replaceEvent = new Event() {\n"
            + "  @Override public void on(Listener l) {\n"
            + "    l.on${contextName}Replaced(${parentType}.this, "
            + "java.util.Collections.unmodifiableList(valueList));\n"
            + "  }\n"
            + "};\n"
            + "for (EventListener l : myListeners) {\n"
            + "  l.on(replaceEvent);\n"
            + "}\n"
            ;
    
    private static final Template SUBSCRIBE_IF_NOT_NULL_TEMPL;
    private static final String SUBSCRIBE_IF_NOT_NULL_CODE = ""
            + "final ${fieldType} value = ${keyParam}.getValue();\n"
            + "if (value != null) {\n"
            + "  ${fieldType}.Subscription elSub =\n"
            + "    value.addListener(\n"
            + "        new ${fieldType}.EventListener() {\n"
            + "          @Override "
            + "public void on(final ${fieldType}.Event e) {\n"
            + "            Event parentE = new Event() {\n"
            + "              @Override public void on(Listener l) {\n"
            + "                int curIndex = \n"
            + "                    my${fieldName}List.indexOf(${keyParam});\n"
            + "                l.on${fieldName}Updated(${parentType}.this,\n"
            + "                    value, curIndex, ${keyParam}, e);\n"
            + "              }\n"
            + "            };\n"
            + "            for (EventListener l : myListeners) {\n"
            + "              l.on(parentE);\n"
            + "            }\n"
            + "          }\n"
            + "        });\n"
            + "  my${fieldName}Subscriptions.put(${keyParam}, elSub);\n"
            + "}\n"
            ;
    
    private static final Template LIST_SET_METHOD_BY_INDEX_TEMPL;
    private static final String LIST_SET_METHOD_BY_INDEX_CODE = ""
            + "${fieldName}Key slot = "
            + "my${fieldName}List.get(${indexParam});\n\n"
            + "set${fieldName}(${indexParam}, slot, ${valueParam});\n"
            ;
    
    private static final Template LIST_SET_METHOD_BY_KEY_TEMPL;
    private static final String LIST_SET_METHOD_BY_KEY_CODE = ""
            + "int index = my${fieldName}List.indexOf(${keyParam});\n"
            + "if (index == -1) {\n"
            + "  throw new IllegalArgumentException();\n"
            + "}\n\n"
            + "set${fieldName}(index, ${keyParam}, ${valueParam});\n"
            ;
    
    private static final Template LIST_SET_METHOD_CORE_TEMPL;
    private static final String LIST_SET_METHOD_CORE_CODE = ""
            + "final ${fieldType} oldType = ${keyParam}.getValue();\n"
            + "${keyParam}.setValue(${valueParam});\n"
            + "<#if !leafFlag>"
            + "if (my${fieldName}Subscriptions.containsKey(${keyParam})) {\n"
            + "  my${fieldName}Subscriptions.remove("
            + "${keyParam}).unsubscribe();\n"
            + "}\n"
            + "subscribeIfNotNull(${keyParam});\n"
            + "</#if>"
            + "Event addEvent = new Event() {\n"
            + "  @Override public void on(Listener l) {\n"
            + "    l.on${fieldName}Set(${parentType}.this, oldType, "
            + "${valueParam}, ${indexParam}, ${keyParam});\n"
            + "  }\n"
            + "};\n"
            + "for (EventListener l : myListeners) {\n"
            + "  l.on(addEvent);\n"
            + "}\n"
            ;
    
    private static final Template LIST_ADD_METHOD_TEMPL;
    private static final String LIST_ADD_METHOD_CODE = ""
            + "final ${fieldName}Key slot = "
            + "new ${fieldName}Key(${valueParam});\n"
            + "my${fieldName}List.add(slot);\n"
            + "final int addedAt = my${fieldName}List.size() - 1;\n"
            + "<#if !leafFlag>"
            + "subscribeIfNotNull(slot);\n"
            + "</#if>"
            + "Event addEvent = new Event() {\n"
            + "  @Override\n"
            + "  public void on(Listener l) {\n"
            + "    l.on${fieldName}Added(${parentType}.this, ${valueParam}, "
            + "addedAt, slot);\n"
            + "  }\n"
            + "};\n"
            + "for (EventListener l : myListeners) {\n"
            + "  l.on(addEvent);\n"
            + "}\n"
            + "return slot;"
            ;
    
    private static final Template LIST_REMOVE_METHOD_BY_INDEX_TEMPL;
    private static final String LIST_REMOVE_METHOD_BY_INDEX_CODE = ""
            + "${fieldName}Key slot = "
            + "my${fieldName}List.remove(${indexParam});\n\n"
            + "remove${fieldName}(${indexParam}, slot);\n"
            ;
    
    private static final Template LIST_REMOVE_METHOD_BY_KEY_TEMPL;
    private static final String LIST_REMOVE_METHOD_BY_KEY_CODE = ""
            + "int index = my${fieldName}List.indexOf(${keyParam});\n"
            + "if (index == -1) {\n"
            + "  throw new IllegalArgumentException();\n"
            + "}\n\n"
            + "remove${fieldName}(index, ${keyParam});\n"
            ;
    
    private static final Template LIST_REMOVE_METHOD_CORE_TEMPL;
    private static final String LIST_REMOVE_METHOD_CORE_CODE = ""
            + "<#if !leafFlag>"
            + "if (my${fieldName}Subscriptions.containsKey(${keyParam})) {\n"
            + "  my${fieldName}Subscriptions.remove("
            + "${keyParam}).unsubscribe();\n"
            + "}\n\n"
            + "</#if>"
            + "my${fieldName}List.remove(${keyParam});\n"
            + "Event removeEvent = new Event() {\n"
            + "  @Override public void on(Listener l) {\n"
            + "    l.on${fieldName}Removed(${parentType}.this, "
            + "${keyParam}.getValue(), ${indexParam}, ${keyParam});\n"
            + "  }\n"
            + "};\n"
            + "for (EventListener l : myListeners) {\n"
            + "  l.on(removeEvent);\n"
            + "}\n"
            ;
    
    private static final Template SUBSCRIBE_TEMPL;
    private static final String SUBSCRIBE_CODE = ""
            + "<#if !finalFlag>"
            + "if (my${fieldName} == null) {\n"
            + "  my${fieldName}Subscription = null;\n"
            + "}\n"
            + "else {\n"
            + "  my${fieldName}Subscription = "
            + "<#else>"
            + "if (my${fieldName} != null) {\n  "
            + "</#if>"
            + "my${fieldName}.addListener(\n"
            + "      new ${fieldType}.EventListener() {\n"
            + "        public void on(final ${fieldType}.Event e) {\n"
            + "          Event parentE = new Event() {\n"
            + "            @Override public void on(Listener l) {\n"
            + "              l.on${fieldName}Updated(${parentType}.this,\n"
            + "                  my${fieldName}, e);\n"
            + "            }\n"
            + "          };\n"
            + "          for (EventListener l : myListeners) {\n"
            + "            l.on(parentE);\n"
            + "          }\n"
            + "        }\n"
            + "      });\n"
            + "}\n"
            ;
    
    private static final Template SET_METHOD_TEMPL;
    private static final String SET_METHOD_CODE = ""
            + "my${fieldName} = ${valueParam};\n"
            + "<#if !leafFlag>\n"
            + "if (my${fieldName}Subscription != null) {\n"
            + "  my${fieldName}Subscription.unsubscribe();\n"
            + "}\n"
            + "</#if>\n"
            + "${afterUnsubscribe}"
            + "Event setEvent = new Event() {\n"
            + "  @Override public void on(Listener l) {\n"
            + "    l.on${contextName}Set(${parentType}.this, my${fieldName});\n"
            + "  }\n"
            + "};\n"
            + "for (EventListener l : myListeners) {\n"
            + "  l.on(setEvent);\n"
            + "}\n"
            ;
            
    
    static {
        Configuration c = new Configuration(Configuration.VERSION_2_3_24);
        SUBSCRIBE_TEMPL = template(SUBSCRIBE_CODE, c);
        SET_METHOD_TEMPL = template(SET_METHOD_CODE, c);
        LIST_ADD_METHOD_TEMPL = template(LIST_ADD_METHOD_CODE, c);
        LIST_REMOVE_METHOD_CORE_TEMPL =
                template(LIST_REMOVE_METHOD_CORE_CODE, c);
        LIST_REMOVE_METHOD_BY_INDEX_TEMPL =
                template(LIST_REMOVE_METHOD_BY_INDEX_CODE, c);
        LIST_REMOVE_METHOD_BY_KEY_TEMPL =
                template(LIST_REMOVE_METHOD_BY_KEY_CODE, c);
        LIST_SET_METHOD_CORE_TEMPL = template(LIST_SET_METHOD_CORE_CODE, c);
        LIST_SET_METHOD_BY_INDEX_TEMPL =
                template(LIST_SET_METHOD_BY_INDEX_CODE, c);
        LIST_SET_METHOD_BY_KEY_TEMPL =
                template(LIST_SET_METHOD_BY_KEY_CODE, c);
        REPLACE_LIST_METHOD_TEMPL = template(REPLACE_LIST_METHOD_CODE, c);
        RETURN_LIST_METHOD_TEMPL = template(RETURN_LIST_METHOD_CODE, c);
        LIST_ITERATOR_TEMPL = template(LIST_ITERATOR_CODE, c);
        
        SUBSCRIBE_IF_NOT_NULL_TEMPL = template(SUBSCRIBE_IF_NOT_NULL_CODE, c);
    }
    
    private static Template template(String code, Configuration config) {
        Template result;
        try {
            result = new Template(null, new StringReader(code), config);
        }
        catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return result;
    }
    
    public static void generate(String packageName, String rootModelName,
            Object descriptor, Appendable out)
            throws IOException {
        ModelClass r = new ModelClass(rootModelName, false);
        outfitModel(r, "", descriptor);
        
        JavaFile output =
                JavaFile.builder(packageName, r.buildTypeSpec()).build();
        
        output.writeTo(out);
    }
    
    private static void outfitModel(
            ModelClass dest, String contextName, Object modelDesc) {
        if (modelDesc instanceof List) {
            List listDesc = (List) modelDesc;
            
            switch (listDesc.size()) {
                case 1: {
                    outfitModelWithList(dest,
                            contextName, new HashSet<>(), listDesc.get(0));
                    break;
                }
                case 2: {
                    Set<String> options =
                            new HashSet<>((List<String>) listDesc.get(0));
                    outfitModel(dest, contextName, options, listDesc.get(1));
                    break;
                }
                default: {
                    throw new RuntimeException();
                }
            }
        }
        else {
            outfitModel(dest, contextName, new HashSet<>(), modelDesc);
        }
    }
    
    private static void outfitModelWithList(ModelClass dest,
            String contextName, Set<String> listOptions, Object elDesc) {
        boolean immutableFlag = listOptions.contains("immutable");
        boolean replaceableFlag = listOptions.contains("replaceable");
        
        ListElementTypeData elTypeData =
                outfitModelWithListElementType(dest, contextName, elDesc);
        String elTypeRaw = elTypeData.getRawTypeName();
        TypeName elType = ClassName.get("", elTypeRaw);
        
        String slotTypeRaw = contextName + "Key";
        TypeName slotType = ClassName.get("", slotTypeRaw);
        
        TypeSpec.Builder keyType = TypeSpec.classBuilder(slotTypeRaw)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(elType, "value")
                        .addStatement("myValue = value")
                        .build())
                .addMethod(MethodSpec.methodBuilder("getValue")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(elType)
                        .addStatement("return myValue")
                        .build());
        
        if (elTypeData.isFinal() || immutableFlag) {
            keyType.addField(
                    elType, "myValue", Modifier.PRIVATE, Modifier.FINAL);
        }
        else {
            keyType.addField(elType, "myValue", Modifier.PRIVATE);
            keyType.addMethod(MethodSpec.methodBuilder("setValue")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(elType, "value")
                        .addStatement("myValue = value")
                        .build());
        }
        
        dest.addType(keyType.build());
        
        FieldSpec.Builder field = FieldSpec.builder(
                ParameterizedTypeName.get(
                        ClassName.get("java.util", "List"), slotType),
                "my" + contextName + "List", Modifier.PRIVATE)
                .initializer(
                        "new $T<>()", ClassName.get("java.util", "LinkedList"));
        
        if (!replaceableFlag) {
            field.addModifiers(Modifier.FINAL);
        }
        
        dest.addField(field.build());
        
        dest.addRootMethod("get" + contextName + "Element", elType,
                ImmutableList.of(ImmutablePair.of("index", TypeName.INT)),
                CodeBlock.builder()
                        .addStatement("return my$LList.get(index).getValue()",
                                contextName)
                        .build());
        
        dest.addRootMethod("get" + contextName + "Element", elType,
                ImmutableList.of(ImmutablePair.of("key", slotType)),
                CodeBlock.builder()
                        .addStatement("int index = my$LList.indexOf(key)",
                                contextName)
                        .addStatement("return my$LList.get(index).getValue()",
                                contextName)
                        .build());
        
        if (contextName.isEmpty()) {
            dest.addImplements(ParameterizedTypeName.get(
                    ClassName.get("", "Iterable"), elType));
            dest.addOverriddenRootMethod("iterator", 
                    ParameterizedTypeName.get(
                            ClassName.get(Iterator.class), elType),
                    ImmutableList.of(),
                    renderCodeBlock(LIST_ITERATOR_TEMPL,
                                    "elementType", elTypeRaw,
                                    "contextName", contextName,
                                    "baseIterable",
                                            "my" + contextName + "List"));
        }
        else {
            // Gross workaround here.  JavaPoet doesn't provide any way to
            // include a random non-static import.  So we render out a template
            // that happens to include an unresolved $T, then replace it with
            // the type we want to import.
            dest.addRootMethod(
                    CaseFormat.UPPER_CAMEL.to(
                            CaseFormat.LOWER_CAMEL, contextName),
                    ParameterizedTypeName.get(
                            ClassName.get("", "Iterable"), elType),
                    ImmutableList.of(),
                    CodeBlock.of(
                            renderTemplate(RETURN_LIST_METHOD_TEMPL,
                                    "elementType", elTypeRaw,
                                    "iteratorCode", renderTemplate(
                                            LIST_ITERATOR_TEMPL,
                                            "elementType", elTypeRaw,
                                            "contextName", contextName,
                                            "baseIterable", "my" + contextName 
                                                    + "List")),
                            ParameterizedTypeName.get(
                                    ClassName.get(Iterator.class), elType)));
        }
        
        if (immutableFlag) {
            String elementParam = CaseFormat.UPPER_CAMEL.to(
                    CaseFormat.LOWER_CAMEL, contextName + "Elements");
            dest.addInitializationParameter(elementParam,
                    ParameterizedTypeName.get(
                            ClassName.get("", "Iterable"), elType));
            
            CodeBlock.Builder init = CodeBlock.builder()
                    .beginControlFlow("for ($T e : $L)", elType, elementParam)
                    .addStatement(
                            "$LKey k = new $LKey(e);", contextName, contextName)
                    .addStatement("my$LList.add(k)", contextName);
            
            if (!elTypeData.isLeaf()) {
                init.addStatement("subscribeIfNotNull(k)");
            }
            
            init.endControlFlow();
            dest.addInitializationCode(init.build());
        }
        
        if (replaceableFlag) {
            dest.addListenerEvent(contextName + "Replaced", ImmutableList.of(
                    ImmutablePair.of("newValue", ParameterizedTypeName.get(
                        ClassName.get("java.util", "List"), elType))));
            
            dest.addRootMethod("replace" + contextName, TypeName.VOID,
                    ImmutableList.of(
                            ImmutablePair.of("elements", 
                                    ParameterizedTypeName.get(
                                            ClassName.get("", "Iterable"),
                                            elType))),
                    renderCodeBlock(REPLACE_LIST_METHOD_TEMPL,
                            "contextName", contextName,
                            "elementType", elTypeRaw,
                            "parentType", dest.getName(),
                            "elementsParam", "elements",
                            "leafFlag", elTypeData.isLeaf()));
            
            dest.addBuildCode(CodeBlock.builder()
                    .beginControlFlow("")
                    .addStatement("List<$T> valueList = new $T<>()",
                            elType, LinkedList.class)
                    .beginControlFlow(
                            "for ($T s : my$LList)", slotType, contextName)
                    .addStatement("valueList.add(s.getValue())")
                    .endControlFlow()
                    .addStatement("l.on$LReplaced(this, "
                            + "$T.unmodifiableList(valueList))",
                            contextName, Collections.class)
                    .endControlFlow()
                    .build());
        }
        
        if (!elTypeData.isLeaf()) {
            dest.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(
                            ClassName.get("java.util", "Map"), slotType,
                            ClassName.get("", elTypeRaw + ".Subscription")),
                    "my" + contextName + "Subscriptions",
                    Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(
                            "new $T<>()", ClassName.get("java.util", "HashMap"))
                    .build());
            
            dest.addListenerEvent(contextName + "Updated", ImmutableList.of(
                    ImmutablePair.of("updatedElement", elType),
                    ImmutablePair.of("index", TypeName.INT),
                    ImmutablePair.of("key", slotType),
                    ImmutablePair.of("event",
                            ClassName.get("", elTypeRaw + ".Event"))));
            dest.addRootMethod(MethodSpec.methodBuilder("subscribeIfNotNull")
                .returns(TypeName.VOID)
                .addModifiers(Modifier.PRIVATE)
                .addParameter(slotType, "key", Modifier.FINAL)
                .addCode(renderCodeBlock(SUBSCRIBE_IF_NOT_NULL_TEMPL,
                        "keyParam", "key",
                        "fieldName", contextName,
                        "fieldType", elTypeRaw,
                        "parentType", dest.getName()))
                .build());
        }
        
        if (!immutableFlag) {
            if (!replaceableFlag) {
                dest.addBuildCode(CodeBlock.builder()
                        .beginControlFlow("")
                        .addStatement("int i = 0")
                        .beginControlFlow(
                                "for ($T s : my$LList)", slotType, contextName)
                        .addStatement("l.on$LAdded(this, s.getValue(), i, s)",
                                contextName)
                        .addStatement("i++")
                        .endControlFlow()
                        .endControlFlow()
                        .build());
            }

            dest.addListenerEvent(contextName + "Added", ImmutableList.of(
                    ImmutablePair.of("addedElement", elType),
                    ImmutablePair.of("index", TypeName.INT),
                    ImmutablePair.of("key", slotType)));
            dest.addRootMethod("add" + contextName, slotType,
                    ImmutableList.of(
                            ImmutablePair.of("newElement", elType)), 
                            renderCodeBlock(LIST_ADD_METHOD_TEMPL,
                                    "valueParam", "newElement",
                                    "fieldName", contextName,
                                    "fieldType", elTypeRaw,
                                    "leafFlag", elTypeData.isLeaf(),
                                    "parentType", dest.getName()));

            dest.addListenerEvent(contextName + "Removed", ImmutableList.of(
                    ImmutablePair.of("removedElement", elType),
                    ImmutablePair.of("index", TypeName.INT),
                    ImmutablePair.of("key", slotType)));
            dest.addRootMethod("remove" + contextName, TypeName.VOID,
                    ImmutableList.of(ImmutablePair.of("index", TypeName.INT)),
                    renderCodeBlock(LIST_REMOVE_METHOD_BY_INDEX_TEMPL,
                            "indexParam", "index",
                            "fieldName", contextName));
            dest.addRootMethod("remove" + contextName, TypeName.VOID,
                    ImmutableList.of(ImmutablePair.of("key", slotType)),
                    renderCodeBlock(LIST_REMOVE_METHOD_BY_KEY_TEMPL,
                            "keyParam", "key",
                            "fieldName", contextName));
            dest.addRootMethod(MethodSpec.methodBuilder("remove" + contextName)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(TypeName.VOID)
                    .addParameter(TypeName.INT, "index", Modifier.FINAL)
                    .addParameter(slotType, "key", Modifier.FINAL)
                    .addCode(renderCodeBlock(LIST_REMOVE_METHOD_CORE_TEMPL,
                            "fieldName", contextName,
                            "keyParam", "key",
                            "indexParam", "index",
                            "leafFlag", elTypeData.isLeaf(),
                            "parentType", dest.getName()))
                    .build());

            if (!elTypeData.isFinal()) {
                dest.addListenerEvent(contextName + "Set", ImmutableList.of(
                        ImmutablePair.of("oldValue", elType),
                        ImmutablePair.of("newValue", elType),
                        ImmutablePair.of("index", TypeName.INT),
                        ImmutablePair.of("key", slotType)));
                dest.addRootMethod("set" + contextName, TypeName.VOID,
                        ImmutableList.of(
                                ImmutablePair.of("index", TypeName.INT),
                                ImmutablePair.of("newValue", elType)),
                        renderCodeBlock(LIST_SET_METHOD_BY_INDEX_TEMPL,
                                "fieldName", contextName,
                                "indexParam", "index",
                                "valueParam", "newValue"));
                dest.addRootMethod("set" + contextName, TypeName.VOID,
                    ImmutableList.of(
                            ImmutablePair.of("key", slotType),
                            ImmutablePair.of("newValue", elType)),
                    renderCodeBlock(LIST_SET_METHOD_BY_KEY_TEMPL,
                            "keyParam", "key",
                            "fieldName", contextName,
                            "valueParam", "newValue"));
                dest.addRootMethod(MethodSpec.methodBuilder("set" + contextName)
                    .returns(TypeName.VOID)
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(TypeName.INT, "index", Modifier.FINAL)
                    .addParameter(slotType, "key", Modifier.FINAL)
                    .addParameter(elType, "value", Modifier.FINAL)
                    .addCode(renderCodeBlock(LIST_SET_METHOD_CORE_TEMPL,
                            "keyParam", "key",
                            "indexParam", "index",
                            "valueParam", "value",
                            "leafFlag", elTypeData.isLeaf(),
                            "fieldName", contextName,
                            "fieldType", elTypeRaw,
                            "parentType", dest.getName()))
                    .build());
            }
        }
    }
    
    private static ListElementTypeData outfitModelWithListElementType(
            ModelClass dest, String contextName, Object elDesc) {
        ListElementTypeData result;
        
        if (elDesc instanceof List) {
            List listElDesc = (List) elDesc;
            switch (listElDesc.size()) {
                case 2: {
                    Set<String> options = ImmutableSet.copyOf(
                            (List<String>) listElDesc.get(0));
                            
                    result = outfitModelWithListElementType(
                            dest, contextName, options, listElDesc.get(1));
                    break;
                }
                default: {
                    throw new RuntimeException();
                }
            }
        }
        else {
            result = outfitModelWithListElementType(
                            dest, contextName, new HashSet<>(), elDesc);
        }
        
        return result;
    }
    
    private static ListElementTypeData outfitModelWithListElementType(
            ModelClass dest, String contextName, Set<String> options,
            Object elDesc) {
        ListElementTypeData result;
        
        if (elDesc instanceof String) {
            result = new ListElementTypeData(options.contains("final"),
                    options.contains("leaf"), (String) elDesc);
        }
        else if (elDesc instanceof Map) {
            String rawElementName = contextName + "Record";
            ModelClass r = new ModelClass(rawElementName, true);
            outfitModel(r, "", elDesc);
            
            boolean leaf =
                    options.contains("leaf") || r.getListenerEventCount() == 0;
            
            dest.addType(r.buildTypeSpec());
            
            result = new ListElementTypeData(
                    options.contains("final"), leaf, rawElementName);
        }
        else {
            throw new RuntimeException();
        }
        
        return result;
    }
    
    private static void outfitModel(ModelClass dest, String contextName,
            Set<String> options, Object modelDesc) {
        boolean finalFlag = options.contains("final");
        boolean leafFlag = options.contains("leaf");
        
        String fieldName = contextName;
        if (fieldName.isEmpty()) {
            fieldName = "Value";
        }
        
        if (modelDesc instanceof String) {
            String fieldTypeString = (String) modelDesc;
            TypeName fieldType = typeName(fieldTypeString);
            
            FieldSpec.Builder fieldBuild = FieldSpec.builder(
                    fieldType, "my" + fieldName, Modifier.PRIVATE);
            
            dest.addRootMethod("get" + fieldName, fieldType, ImmutableList.of(),
                    CodeBlock.builder()
                            .addStatement("return my$L", fieldName).build());
            
            if (!leafFlag) {
                dest.addListenerEvent(contextName + "Updated", ImmutableList.of(
                        ImmutablePair.of("value", fieldType),
                        ImmutablePair.of("event", ClassName.get(
                                "", fieldTypeString + ".Event"))));
            }
            
            if (finalFlag) {
                fieldBuild.addModifiers(Modifier.FINAL);
                
                String paramName = CaseFormat.UPPER_CAMEL.to(
                        CaseFormat.LOWER_CAMEL, fieldName);
                dest.addInitializationParameter(paramName, fieldType);
                dest.addInitializationCode(CodeBlock.builder()
                        .addStatement("my$L = $L", fieldName, paramName)
                        .build());
                
                if (!leafFlag) {
                    dest.addInitializationCode(
                            renderCodeBlock(SUBSCRIBE_TEMPL,
                                    "finalFlag", finalFlag,
                                    "fieldName", fieldName,
                                    "fieldType", fieldTypeString,
                                    "parentType", dest.getName()
                            ));
                }
            }
            else {
                String subscribeCode;
                if (leafFlag) {
                    subscribeCode = "";
                }
                else {
                    FieldSpec.Builder subscriptionField = FieldSpec.builder(
                            ClassName.get(
                                    "", fieldTypeString + ".Subscription"),
                            "my" + fieldName + "Subscription",
                            Modifier.PRIVATE);
                    dest.addField(subscriptionField.build());
                    
                    subscribeCode =
                            renderTemplate(
                                    SUBSCRIBE_TEMPL,
                                    "finalFlag", finalFlag,
                                    "fieldName", fieldName,
                                    "fieldType", fieldTypeString,
                                    "parentType", dest.getName()
                            );
                }
                
                dest.addBuildCode(CodeBlock.builder()
                        .addStatement(
                                "l.on$LSet(this, my$L)", contextName, fieldName)
                        .build());
                
                dest.addListenerEvent(contextName + "Set", ImmutableList.of(
                        ImmutablePair.of("newValue", fieldType)));
                
                dest.addRootMethod("set" + contextName, TypeName.VOID, 
                    ImmutableList.of(
                            ImmutablePair.of("newValue", fieldType)),
                            renderCodeBlock(SET_METHOD_TEMPL,
                                    "parentType", dest.getName(),
                                    "fieldName", fieldName,
                                    "valueParam", "newValue",
                                    "leafFlag", leafFlag,
                                    "contextName", contextName,
                                    "afterUnsubscribe", subscribeCode));
            }
            
            dest.addField(fieldBuild.build());
        }
        else if (modelDesc instanceof List) {
            List listDesc = (List) modelDesc;
            
            switch (listDesc.size()) {
                case 1: {
                    outfitModelWithList(dest, contextName, options,
                            listDesc.get(0));
                    break;
                }
                case 2: {
                    outfitModelWithList(dest, contextName, options, listDesc);
                    break;
                }
                default: {
                    throw new RuntimeException();
                }
            }
        }
        else if (modelDesc instanceof Map) {
            Map<String, Object> mapDesc = (Map<String, Object>) modelDesc;
            for (Map.Entry<String, Object> field : mapDesc.entrySet()) {
                outfitModel(
                        dest, contextName + field.getKey(), field.getValue());
            }
        }
        else {
            throw new RuntimeException(modelDesc.getClass().getSimpleName());
        }
    }
    
    private static CodeBlock renderCodeBlock(Template t, Object ... data) {
        return CodeBlock.builder().add(renderTemplate(t, data)).build();
    }
    
    private static String renderTemplate(Template t, Object ... data) {
        Map<String, Object> dataMap = new HashMap<>();
        for (int i = 0; i < data.length; i += 2) {
            dataMap.put((String) data[i], data[i + 1]);
        }
        
        StringWriter w = new StringWriter();
        
        try {
            t.process(dataMap, w);
        }
        catch (TemplateException | IOException e) {
            throw new RuntimeException(e);
        }
        
        return w.toString();
    }
    
    private static TypeName typeName(String rawName) {
        TypeName result;
        switch (rawName) {
            case "boolean": {
                result = TypeName.BOOLEAN;
                break;
            }
            case "byte": {
                result = TypeName.BYTE;
                break;
            }
            case "char": {
                result = TypeName.CHAR;
                break;
            }
            case "double": {
                result = TypeName.DOUBLE;
                break;
            }
            case "float": {
                result = TypeName.FLOAT;
                break;
            }
            case "int": {
                result = TypeName.INT;
                break;
            }
            case "long": {
                result = TypeName.LONG;
                break;
            }
            case "short": {
                result = TypeName.SHORT;
                break;
            }
            default: {
                result = ClassName.get("", rawName);
                break;
            }
        }
        return result;
    }
    
    private static class ListElementTypeData {
        private final boolean myFinalFlag;
        private final boolean myLeafFlag;
        private final String myRawTypeName;
        
        public ListElementTypeData(boolean finalFlag, boolean leafFlag,
                String rawTypeName) {
            myFinalFlag = finalFlag;
            myLeafFlag = leafFlag;
            myRawTypeName = rawTypeName;
        }
        
        public boolean isFinal() {
            return myFinalFlag;
        }
        
        public boolean isLeaf() {
            return myLeafFlag;
        }
        
        public String getRawTypeName() {
            return myRawTypeName;
        }
    }
}
