/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viewmodelgenerator;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import freemarker.template.Version;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author hamptos
 */
public class ViewModelGenerator {
    private static final Template SUBSCRIBE_TEMPL;
    private static final Template SET_METHOD_TEMPL;
    
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
            + "        void on(${fieldType}.Event e) {\n"
            + "          for (Listener l : myListeners) {\n"
            + "            l.on${fieldName}Update(${parentType}.this,\n"
            + "                my${fieldName}, e);\n"
            + "          }\n"
            + "        }\n"
            + "      });\n"
            + "}\n";
    
    private static final String SET_METHOD_CODE = ""
            + "my${fieldName} = ${valueParam};\n"
            + "<#if !leafFlag>\n"
            + "if (my${fieldName}Subscription != null) {\n"
            + "  my${fieldName}Subscription.unsubscribe();\n"
            + "}\n"
            + "</#if>\n"
            + "${afterUnsubscribe}"
            + "for (Listener l : myListeners) {\n"
            + "  l.on${contextName}Set(this, my${fieldName});\n"
            + "}\n"
            ;
            
    
    static {
        Configuration c = new Configuration(Configuration.VERSION_2_3_24);
        SUBSCRIBE_TEMPL = template(SUBSCRIBE_CODE, c);
        SET_METHOD_TEMPL = template(SET_METHOD_CODE, c);
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
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
            throws FileNotFoundException, IOException {
        File f = new File(args[0]);
        Scanner s = new Scanner(f);
        ExtractedData e = parseFile(s);
        
        String fileName = f.getName();
        String rootContextName =
                fileName.substring(0, fileName.length() - ".java".length());
        
        ModelClass r = new ModelClass(rootContextName);
        outfitModel(r, "", e.getModelDescription());
        
        JavaFile output =
                JavaFile.builder(e.getPackage(), r.buildTypeSpec()).build();
        output.writeTo(System.out);
    }
    
    private static void outfitModel(
            ModelClass dest, String contextName, Object modelDesc) {
        if (modelDesc instanceof List) {
            List listDesc = (List) modelDesc;
            
            switch (listDesc.size()) {
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
        else if (modelDesc instanceof Map) {
            Map<String, Object> mapDesc = (Map<String, Object>) modelDesc;
            for (Map.Entry<String, Object> field : mapDesc.entrySet()) {
                outfitModel(dest, field.getKey(), field.getValue());
            }
        }
        else if (modelDesc instanceof String) {
            outfitModel(dest, contextName, new HashSet<>(), modelDesc);
        }
        else {
            throw new RuntimeException();
        }
    }
    
    private static void outfitModel(ModelClass dest, String contextName,
            Set<String> options, Object modelDesc) {
        boolean finalFlag = options.contains("final");
        boolean leafFlag = options.contains("leaf");
        
        String fieldName = contextName;
        if (fieldName.isEmpty()) {
            fieldName = "Value";
        }
        
        FieldSpec field;
        if (modelDesc instanceof String) {
            String fieldTypeString = (String) modelDesc;
            TypeName fieldType = ClassName.get("", fieldTypeString);
            
            FieldSpec.Builder fieldBuild = FieldSpec.builder(
                    fieldType, "my" + fieldName, Modifier.PRIVATE);
            
            if (!leafFlag) {
                dest.addListenerEvent(contextName + "Update", ImmutableList.of(
                        ImmutablePair.of("value", fieldType),
                        ImmutablePair.of("event", ClassName.get(
                                "", fieldTypeString + ".Event"))));
            }
            
            if (finalFlag) {
                fieldBuild.addModifiers(Modifier.FINAL);
                dest.addInitializationParameter(fieldName, fieldType);
                
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
                if (!leafFlag) {
                    FieldSpec.Builder subscriptionField = FieldSpec.builder(
                            ClassName.get(
                                    "", fieldTypeString + ".Subscription"),
                            "my" + fieldName + "Subscription",
                            Modifier.PRIVATE);
                    dest.addField(subscriptionField.build());
                }
                
                dest.addListenerEvent(contextName + "Set", ImmutableList.of(
                        ImmutablePair.of("newValue", fieldType)));
                
                dest.addRootMethod("set" + contextName, TypeName.VOID, 
                    ImmutableList.of(
                            ImmutablePair.of("newValue", fieldType)),
                            renderCodeBlock(SET_METHOD_TEMPL,
                                    "fieldName", fieldName,
                                    "valueParam", "newValue",
                                    "leafFlag", leafFlag,
                                    "contextName", contextName,
                                    "afterUnsubscribe", renderTemplate(
                                            SUBSCRIBE_TEMPL,
                                            "finalFlag", finalFlag,
                                            "fieldName", fieldName,
                                            "fieldType", fieldTypeString,
                                            "parentType", dest.getName()
                                    )));
            }
            
            field = fieldBuild.build();
        }
        else {
            throw new RuntimeException(modelDesc.getClass().getSimpleName());
        }
        
        dest.addField(field);
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
    
    private static ExtractedData parseFile(Scanner s) {
        String result = "";
        String pkg = null;
        
        boolean started = false;
        boolean done = false;
        while (s.hasNextLine()) {
            String line = s.nextLine().trim();
            
            if (line.startsWith("package")) {
                if (pkg == null) {
                    pkg = line.substring("package".length())
                            .trim().split(";")[0];
                }
                else {
                    throw new RuntimeException("Multiple 'package' lines.");
                }
            }
            if (done && line.startsWith("/*%")) {
                throw new RuntimeException("Multiple view defs.");
            }
            else if (!started && line.startsWith("/*%")) {
                started = true;
                result += line.substring(3);
            }
            else if (started && line.contains("*/")) {
                started = false;
                done = true;
                result += "\n" + line.substring(0, line.indexOf("*/"));
            }
            else if (started) {
                if (!line.startsWith("%")) {
                    throw new RuntimeException(
                            "View def lines must start with '%'. Got: " + line);
                }
                
                line = line.substring(1);
                result += "\n" + line;
            }
        }
        
        if (pkg == null) {
            throw new RuntimeException("No 'package' line.");
        }
        
        return new ExtractedData(new Yaml().load(result), pkg);
    }
    
    private static class ExtractedData {
        private final Object myModelDescription;
        private final String myPackage;
        
        public ExtractedData(Object modelDesc, String pkg) {
            myModelDescription = modelDesc;
            myPackage = pkg;
        }
        
        public Object getModelDescription() {
            return myModelDescription;
        }
        
        public String getPackage() {
            return myPackage;
        }
    }
}
