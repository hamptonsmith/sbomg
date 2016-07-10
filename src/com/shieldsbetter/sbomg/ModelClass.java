/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.shieldsbetter.sbomg;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 *
 * @author hamptos
 */
public class ModelClass {
    private final String myName;
    private final boolean myStaticFlag;
    
    private final AbstractMethodSet myRootListener = new AbstractMethodSet();
    private final Map<String, AbstractMethodSet> myAuxiliaryListeners =
            new HashMap<>();
    
    private final List<TypeSpec> myRootTypes = new LinkedList<>();
    private final List<MethodSpec> myRootMethods = new LinkedList();
    
    private final List<ModelClass> mySubModels = new LinkedList<>();
    
    private final List<FieldSpec> myFields = new LinkedList<>();
    private final List<Pair<String, TypeName>> myInitializationParams =
            new LinkedList<>();
    
    private final CodeBlock.Builder myAdditionalInitializationCode =
            CodeBlock.builder();
    
    private final CodeBlock.Builder myBuildCode = CodeBlock.builder();
    
    private final List<TypeName> myImplementsList = new LinkedList<>();
    
    public ModelClass(String name, boolean staticFlag) {
        myName = name;
        myStaticFlag = staticFlag;
    }
    
    public void addInitializationCode(CodeBlock b) {
        myAdditionalInitializationCode.add(b);
    }
    
    public void addInitializationParameter(String fieldNoMy, TypeName type) {
        myInitializationParams.add(new ImmutablePair<>(fieldNoMy, type));
    }
    
    public void addField(boolean finalFlag, TypeName type, String name) {
        FieldSpec.Builder f =
                FieldSpec.builder(type, name).addModifiers(Modifier.PRIVATE);
        if (finalFlag) {
            f.addModifiers(Modifier.FINAL);
        }
        
        myFields.add(f.build());
    }
    
    public void addField(FieldSpec f) {
        myFields.add(f);
    }
    
    public String getName() {
        return myName;
    }
    
    public void addSubModel(ModelClass c) {
        mySubModels.add(c);
    }
    
    public void addType(TypeSpec t) {
        myRootTypes.add(t);
    }
    
    public void addBuildCode(CodeBlock c) {
        myBuildCode.add(c);
    }
    
    public TypeSpec buildTypeSpec() {
        TypeSpec.Builder result =
                TypeSpec.classBuilder(myName).addModifiers(Modifier.PUBLIC);
        
        if (myStaticFlag) {
            result.addModifiers(Modifier.STATIC);
        }
        
        TypeName thisTypeName = ClassName.get("", myName);
        
        for (Map.Entry<String, AbstractMethodSet> auxListenerDef
                : myAuxiliaryListeners.entrySet()) {
            TypeSpec.Builder auxListener =
                    TypeSpec.interfaceBuilder(auxListenerDef.getKey())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
            
            AbstractMethodSet methodDefs = auxListenerDef.getValue();
            for (String methodName : methodDefs.getNames()) {
                MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC);
                
                method.addParameter(thisTypeName, "source");
                
                for (Pair<String, TypeName> parameter
                        : methodDefs.getParameters(methodName)) {
                    method.addParameter(
                            parameter.getRight(), parameter.getLeft());
                }
                
                auxListener.addMethod(method.build());
            }
            
            result.addType(auxListener.build());
        }
        
        TypeSpec.Builder listener = TypeSpec.interfaceBuilder("Listener")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        for (String methodName : myRootListener.getNames()) {
            MethodSpec.Builder method =
                    MethodSpec.methodBuilder("on" + methodName)
                            .addModifiers(
                                    Modifier.ABSTRACT, Modifier.PUBLIC);

            method.addParameter(thisTypeName, "source");

            for (Pair<String, TypeName> parameter
                    : myRootListener.getParameters(methodName)) {
                method.addParameter(
                        parameter.getRight(), parameter.getLeft());
            }

            listener.addMethod(method.build());
        }
        result.addType(listener.build());

        TypeSpec.Builder event = TypeSpec.interfaceBuilder("Event")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addMethod(MethodSpec.methodBuilder("on")
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(ClassName.get("", "Listener"), "l")
                        .build());
        result.addType(event.build());

        TypeSpec.Builder eventListener =
                TypeSpec.interfaceBuilder("EventListener")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addMethod(MethodSpec.methodBuilder("on")
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(ClassName.get("", "Event"), "e")
                        .build());
        result.addType(eventListener.build());

        TypeSpec.Builder subscription =
                TypeSpec.interfaceBuilder("Subscription")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addMethod(MethodSpec.methodBuilder("unsubscribe")
                                .addModifiers(
                                        Modifier.ABSTRACT, Modifier.PUBLIC)
                                .build());
        result.addType(subscription.build());

        CodeBlock.Builder addListenerEventListenerCode = CodeBlock.builder();
        
        if (myRootListener.getMethodCount() > 0) {
            result.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(
                            ClassName.get("java.util", "List"),
                            ClassName.get("", "EventListener")),
                    "myListeners", Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new $T<>()",
                            ClassName.get("java.util", "LinkedList"))
                    .build());
            
            addListenerEventListenerCode
                    .addStatement("myListeners.add(el)")
                    .beginControlFlow("return new Subscription()")
                    .beginControlFlow("@Override public void unsubscribe()")
                    .addStatement("myListeners.remove(el)")
                    .endControlFlow()
                    .endControlFlow("");
        }
        else {
            addListenerEventListenerCode
                    .beginControlFlow("return new Subscription()")
                    .beginControlFlow("@Override public void unsubscribe()")
                    .endControlFlow()
                    .endControlFlow("");
        }

        result.addMethod(MethodSpec.methodBuilder("addListener")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("", "Subscription"))
                .addParameter(
                        ClassName.get("", "Listener"), "l", Modifier.FINAL)
                .addCode(CodeBlock.builder()
                        .beginControlFlow(
                                "EventListener el = new EventListener()")
                        .beginControlFlow("@Override public void on(Event e)")
                        .addStatement("e.on(l)")
                        .endControlFlow()
                        .endControlFlow("")
                        .addStatement("return addListener(el)")
                        .build()).build());
        
        result.addMethod(MethodSpec.methodBuilder("addListener")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("", "Subscription"))
                .addParameter(ClassName.get(
                        "", "EventListener"), "el", Modifier.FINAL)
                .addCode(addListenerEventListenerCode.build())
                .build());
        
        result.addMethod(MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(
                    ClassName.get("", "Listener"), "l", Modifier.FINAL)
            .addCode(myBuildCode.build()).build());
            
        for (FieldSpec f : myFields) {
            result.addField(f);
        }
        
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        for (Pair<String, TypeName> param : myInitializationParams) {
            String paramName = param.getLeft();
            constructor.addParameter(param.getRight(), paramName);
        }
        constructor.addCode(myAdditionalInitializationCode.build());
        result.addMethod(constructor.build());
        
        result.addMethods(myRootMethods);
        
        for (ModelClass c : mySubModels) {
            result.addType(c.buildTypeSpec());
        }
        
        for (TypeSpec type : myRootTypes) {
            result.addType(type);
        }
        
        for (TypeName type : myImplementsList) {
            result.addSuperinterface(type);
        }
        
        return result.build();
    }

    public void addImplements(TypeName parent) {
        myImplementsList.add(parent);
    }
    
    public void addOverriddenRootMethod(String methodName, TypeName returnType,
            Iterable<Pair<String, TypeName>> parameters, CodeBlock code) {
        MethodSpec.Builder b = MethodSpec.methodBuilder(methodName)
                .addAnnotation(java.lang.Override.class)
                .returns(returnType)
                .addCode(code)
                .addModifiers(Modifier.PUBLIC);
        
        for (Pair<String, TypeName> param : parameters) {
            b.addParameter(param.getRight(), param.getLeft(), Modifier.FINAL);
        }
        
        myRootMethods.add(b.build());
    }
    
    public void addRootMethod(String methodName, TypeName returnType,
            Iterable<Pair<String, TypeName>> parameters, CodeBlock code) {
        MethodSpec.Builder b = MethodSpec.methodBuilder(methodName)
                .returns(returnType)
                .addCode(code)
                .addModifiers(Modifier.PUBLIC);
        
        for (Pair<String, TypeName> param : parameters) {
            b.addParameter(param.getRight(), param.getLeft(), Modifier.FINAL);
        }
        
        myRootMethods.add(b.build());
    }
    
    public void addRootMethod(MethodSpec m) {
        myRootMethods.add(m);
    }
    
    public void addListenerEvent(String eventName,
            Iterable<Pair<String, TypeName>> parameters) {
        myRootListener.addMethod(eventName, parameters);
    }
    
    public int getListenerEventCount() {
        return myRootListener.getMethodCount();
    }
    
    public void createAuxiliaryListener(String auxName) {
        myAuxiliaryListeners.put(auxName, new AbstractMethodSet());
    }
    
    public void addAuxiliaryListenerEvent(String auxName, String eventName,
            Iterable<Pair<String, TypeName>> parameters) {
        myAuxiliaryListeners.get(auxName).addMethod(eventName, parameters);
    }
    
    public static class AbstractMethodSet {
        private final Map<String, ImmutableList<Pair<String, TypeName>>>
                myMethods = new HashMap<>();
        
        public void addMethod(String methodName,
                Iterable<Pair<String, TypeName>> parameters) {
            myMethods.put(methodName, ImmutableList.copyOf(parameters));
        }
        
        public Iterable<String> getNames() {
            return myMethods.keySet();
        }
        
        public ImmutableList<Pair<String, TypeName>> getParameters(
                String name) {
            return myMethods.get(name);
        }
        
        public int getMethodCount() {
            return myMethods.size();
        }
    }
    
    public static class ConcreteMethodSet {
        private final Map<String, ImmutableList<Pair<String, TypeName>>>
                myParameters = new HashMap<>();
        private final Map<String, CodeBlock> myCodeBlocks = new HashMap<>();
        private final Map<String, TypeName> myReturnTypes = new HashMap<>();
        
        public void addMethod(String methodName, TypeName returnType,
                Iterable<Pair<String, TypeName>> parameters, CodeBlock code) {
            if (code == null) {
                throw new IllegalArgumentException();
            }
            
            myParameters.put(methodName, ImmutableList.copyOf(parameters));
            myCodeBlocks.put(methodName, code);
            myReturnTypes.put(methodName, returnType);
        }
        
        public Iterable<String> getNames() {
            return myParameters.keySet();
        }
        
        public ImmutableList<Pair<String, TypeName>> getParameters(
                String name) {
            return myParameters.get(name);
        }
        
        public TypeName getReturnType(String name) {
            return myReturnTypes.get(name);
        }
        
        public CodeBlock getCode(String name) {
            if (!myCodeBlocks.containsKey(name)) {
                throw new IllegalArgumentException(name);
            }
            
            return myCodeBlocks.get(name);
        }
    }
    
    public static class FieldData {
        private final TypeName myType;
        private final String myName;
        private final boolean myFinalFlag;
        
        public FieldData(boolean finalFlag, TypeName type, String name) {
            myFinalFlag = finalFlag;
            myType = type;
            myName = name;
        }
        
        public boolean getFinal() {
            return myFinalFlag;
        }
        
        public TypeName getType() {
            return myType;
        }
        
        public String getName() {
            return myName;
        }
    }
}
