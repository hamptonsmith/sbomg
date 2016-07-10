/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viewmodelgenerator;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author hamptos
 */
public final class Cli {
    public static void main(String[] args) {
        int result;
        
        try {
            Options options = new Options();
            options.addOption(
                    "f", false, "Force overwrite of files that already exist.");
            
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            try {
                Plan p = makePlan(cmd);

                for (File input : p.files()) {
                    String modelName = nameWithoutExtension(input);
                    File targetPath = p.getDestinationPath(input);
                    try (
                        Scanner inputScan = fileToScanner("input", input);
                        Writer outputWriter =
                                outputPathToWriter(targetPath, modelName, cmd);
                    ) {
                        Object modelDesc = parseSbomgV1(inputScan);
                        
                        try {
                            ViewModelGenerator.generate(p.getPackage(input),
                                    modelName, modelDesc, outputWriter);
                        }
                        catch (IOException ioe) {
                            throw new FileException(
                                    buildOutputFile(targetPath, modelName),
                                    "writing to target", ioe.getMessage());
                        }
                    }
                    catch (IOException ioe) {
                        throw new FileException(
                                    buildOutputFile(targetPath, modelName),
                                    "opening target", ioe.getMessage());
                    }
                }
                
                result = 0;
            }
            catch (OperationException oe) {
                System.err.println(oe.getMessage());
                result = 1;
            }
        }
        catch (FileException fe) {
            System.err.println("Error " + fe.getTask() + " file "
                    + fe.getFile().getPath() + ":");
            System.err.println("    " + fe.getMessage());
            
            result = 1;
        }
        catch (ParseException pe) {
            throw new RuntimeException(pe);
        }
        
        System.exit(result);
    }
    
    private static Plan makePlan(CommandLine cmd) throws OperationException {
        Plan result;
        String[] args = cmd.getArgs();
        File pwd = new File(System.getProperty("user.dir"));
        
        switch (args.length) {
            case 0: {
                File projectFile = new File(pwd, "sbomg.yaml");
                if (!projectFile.exists()) {
                    throw new OperationException("Zero-argument form must be "
                            + "run in a directory with an sbomg.yaml project "
                            + "descriptor file.");
                }
                result = planFromProjectDescriptor(
                        parseProjectDescriptor(projectFile));
                break;
            }
            case 1: {
                File inputFile = new File(args[0]);
                if (inputFile.getName().equals("sbomg.yaml")) {
                    result = planFromProjectDescriptor(
                            parseProjectDescriptor(inputFile));
                }
                else if (inputFile.getName().endsWith((".sbomg"))) {
                    result = singleFilePlan(
                            inputFile, findProjectDescriptor(inputFile));
                }
                else {
                    throw new OperationException("Input file to one-argument "
                            + "form must indicate an sbomg.yaml project "
                            + "descriptor file or a .sbomg model file rooted "
                            + "in a directory with a project descriptor file.");
                }
                
                break;
            }
            default: {
                if (args.length > 3) {
                    throw new OperationException("Too many arguments.");
                }
                
                String packageSpec = args[1];
                String outputPathFilename;
                if (args.length == 3) {
                    outputPathFilename = args[2];
                }
                else {
                    outputPathFilename = System.getProperty("user.dir");
                }
                
                result = explicitPlan(new File(args[0]), packageSpec,
                        new File(outputPathFilename));
            }
        }
        
        return result;
    }
    
    private static Plan explicitPlan(
            File input, String packageSpec, File outputPath) {
        Plan p = new Plan();
        p.addModel(input, packageSpec, outputPath);
        return p;
    }
    
    private static Plan singleFilePlan(File inputFile, ProjectDescriptor d)
            throws OperationException {
        Plan p = new Plan();
        String packageSpec = d.getPackage(inputFile);
        p.addModel(inputFile, packageSpec,
                new File(d.getTargetDirectory(),
                        packageSpec.replace(".", File.pathSeparator)));
        return p;
    }
    
    private static Plan planFromProjectDescriptor(ProjectDescriptor d)
            throws OperationException {
        Plan p = new Plan();
        for (File srcDir : d.getSourceDirectories()) {
            accumulateModels(srcDir, d, p);
        }
        return p;
    }
    
    private static void accumulateModels(File root, ProjectDescriptor project,
            Plan dest) throws OperationException {
        FileFilter models = (File file) ->
                !file.isDirectory() && file.getName().endsWith(".sbomg");
        FileFilter directories = (File file) -> file.isDirectory();
        
        for (File modelFile : root.listFiles(models)) {
            String packageSpec = project.getPackage(root);
            dest.addModel(modelFile, project.getPackage(modelFile),
                    new File(project.getRoot(),
                            packageSpec.replace(".", File.pathSeparator)));
        }
        
        for (File directory : root.listFiles(directories)) {
            accumulateModels(directory, project, dest);
        }
    }
    
    private static ProjectDescriptor findProjectDescriptor(File start)
            throws OperationException {
        ProjectDescriptor result;
        
        boolean isFile;
        try {
             isFile = start.isFile();
        }
        catch (SecurityException se) {
            throw new OperationException("Reached non-readable directory "
                    + start.getPath() + " without finding sbomg.yaml file.");
        }
        
        if (isFile) {
            result = findProjectDescriptor(start.getParentFile());
        }
        else {
            FileFilter ff = (File file) -> file.getName().equals("sbomg.yaml");
            File[] files = start.listFiles(ff);
            switch (files.length) {
                case 0: {
                    File parent = start.getParentFile();
                    if (parent == null) {
                        throw new OperationException("Reached " 
                                + start.getPath() 
                                + " without finding sbomg.yaml file.");
                    }
                    result = findProjectDescriptor(parent);
                    break;
                }
                case 1: {
                    result = parseProjectDescriptor(start);
                    break;
                }
                default: {
                    throw new VerifyException(
                            "More than one file with the same name?");
                }
            }
        }
        
        return result;
    }
    
    private static ProjectDescriptor parseProjectDescriptor(File f) 
            throws OperationException {
        ProjectDescriptor result;
        try (InputStream fis = new FileInputStream(f)) {
            result = parseProjectDescriptor(f, fis);
        }
        catch (FileNotFoundException fnfe) {
            throw new OperationException(
                    f.getPath() + " could not be opened: " + fnfe.getMessage());
        }
        catch (IOException ioe) {
            throw new OperationException(f.getPath() + " could not be opened: "
                    + ioe.getMessage() + ".");
        }
        
        return result;
    }
    
    private static ProjectDescriptor parseProjectDescriptor(
            File location, InputStream d) throws OperationException {
        String locationPath = location.getPath();
        ProjectDescriptor result;
        Object rawDescriptor;
        try {
            rawDescriptor = new Yaml().load(d);
        }
        catch (RuntimeException re) {
            throw new OperationException("Couldn't parse project descriptor "
                    + locationPath + ": " + re.getMessage());
        }
        
        if (!(rawDescriptor instanceof Map)) {
            throw new OperationException("Project descriptor " + locationPath 
                    + " must be a YAML map.");
        }
        Map rawDescriptorMap = (Map) rawDescriptor;
        
        Object rawSrcDirs = rawDescriptorMap.remove("source directories");
        if (rawSrcDirs == null) {
            throw new OperationException("Project descriptor " + locationPath
                    + " must contain 'source directories' key.  Keys are case "
                    + "sensitive.");
        }

        List rawSrcDirsList;
        if (rawSrcDirs instanceof List) {
            rawSrcDirsList = (List) rawSrcDirs;
        }
        else if (rawSrcDirs instanceof String) {
            rawSrcDirsList = ImmutableList.of(rawSrcDirs);
        }
        else {
            throw new OperationException("In project descriptor " + location
                    + ", 'source directories' key must map to a string or a "
                    + "list of strings.");
        }
        
        List<File> srcDirs = new LinkedList<>();
        for (Object rawSrcDir : rawSrcDirsList) {
            if (!(rawSrcDir instanceof String)) {
                throw new OperationException("In project descriptor " + location
                        + ", 'source directories' contains a non-string "
                        + "element: " + rawSrcDir);
            }
            
            srcDirs.add(new File((String) rawSrcDir));
        }
        
        Object rawTargetDir = rawDescriptorMap.remove("target directory");
        if (rawTargetDir == null) {
            throw new OperationException("Project descriptor " + location
                    + " must contain 'target directory' key.  Keys are case "
                    + "sensitive.");
        }
        
        if (!(rawTargetDir instanceof String)) {
            throw new OperationException("In project descriptor " + location
                    + ", 'target directory' maps to a non-string element: "
                    + rawTargetDir);
        }
        
        File targetDir = new File((String) rawTargetDir);
        return new ProjectDescriptor(
                location.getParentFile(), srcDirs, targetDir);
    }
    
    private static String nameWithoutExtension(File f) {
        String fName = f.getName();
        
        int lastDot = fName.lastIndexOf(".");
        
        String result;
        if (lastDot == -1) {
            result = fName;
        }
        else {
            result = fName.substring(0, lastDot);
        }
        
        return result;
    }
    
    private static Scanner fileToScanner(String fileDesc, File f)
            throws OperationException {
        Scanner result;
        try {
            result = fileToScanner(f);
        }
        catch (FileRelativeException fre) {
            throw new OperationException("Error opening " + fileDesc + " file "
                    + f.getPath() + ":\n" + fre.getMessage());
        }
        return result;
    }
    
    private static Object parseSbomgV1(Scanner s) throws OperationException {
        if (!s.hasNextLine()) {
            throw new OperationException("Empty.");
        }
        
        String magicLine = s.nextLine();
        if (!magicLine.startsWith("sbomg-")) {
            throw new OperationException("Input must begin with 'sbomg-'.");
        }
        
        String version = magicLine.substring("sbomg-".length()).trim();
        if (version.isEmpty()) {
            throw new OperationException(
                    "Format version must follow 'sbomg-'.");
        }
        else if (!version.equals("v1")) {
            throw new OperationException("Unrecognized format version: "
                    + version + ".  Must be 'v1'.");
        }
        
        String modelDescriptorFormatSpecifier =
                expectLine(s, "Expecting model format specifier.  Found EOF.")
                        .trim();
        
        if (!modelDescriptorFormatSpecifier.equals("yaml")) {
            throw new OperationException(
                    "Model format specifier must be 'yaml'. Was: "
                            + modelDescriptorFormatSpecifier);
        }
        
        String yamlBlock = expectBlock(s, "yaml");
        Object modelDescriptorObject = new Yaml().load(yamlBlock);
        
        return modelDescriptorObject;
    }
    
    private static String expectBlock(Scanner input, String blockDesc)
            throws OperationException {
        String block = "";
        String curLine = "dummy";
        while (input.hasNextLine() && !curLine.isEmpty()) {
            curLine = input.nextLine();
            
            if (curLine.startsWith("% ")) {
                block += curLine.substring(("% ".length())) + "\n";
            }
            else {
                if (!curLine.trim().isEmpty()) {
                    if (block.isEmpty()) {
                        throw new OperationException("Expecting a " + blockDesc
                                + "block line starting with '%' followed by a "
                                + "space.");
                    }
                    else {
                        throw new OperationException("Text blocks must be "
                                + "continued by block lines starting with '%' "
                                + "followed by a space, or the block should be"
                                + "terminated by EOF or a blank line.");
                    }
                }
            }
        }
        
        if (block.isEmpty()) {
            throw new OperationException("Expecting a " + blockDesc + " block "
                    + "starting with '%'. Found EOF.");
        }
        
        return block;
    }
    
    private static String expectLine(Scanner input, String failureMessage)
            throws OperationException {
        String result = null;
        
        while ((result == null || result.isEmpty()) && input.hasNextLine()) {
            result = input.nextLine();
        }
        
        if (result == null || result.isEmpty()) {
            throw new OperationException(failureMessage);
        }
        
        return result;
    }
    
    private static File buildOutputFile(File path, String modelName) {
        return new File(path, modelName + ".java");
    }
    
    private static Writer outputPathToWriter(
            File outputPath, String modelName, CommandLine cmd)
            throws OperationException {
        if (outputPath.exists()) {
            if (!outputPath.isDirectory()) {
                throw new OperationException("Output path " 
                        + outputPath.getPath() + " is not a directory.");
            }
        }
        else {
            boolean created = outputPath.mkdirs();
            if (!created) {
                throw new OperationException("Output path "
                        + outputPath.getPath() + " couldn't be created.");
            }
        }
        
        File outputFile = buildOutputFile(outputPath, modelName);
        
        if (outputFile.exists()) {
            if (!cmd.hasOption('f')) {
                throw new OperationException("Output file " 
                        + outputFile.getPath() 
                        + " already exists.  Use -f to force overwrite.");
            }
            
            if (outputFile.isDirectory()) {
                throw new OperationException("Output file " 
                        + outputFile.getPath()
                        + " already exists and is a directory.");
            }
        }
        
        Writer result;
        try {
            result = new FileWriter(outputFile);
        }
        catch (IOException ioe) {
            throw new OperationException("Error opening output file "
                    + outputFile.getPath() + ":\n\n" + ioe.getMessage());
        }
        
        return result;
    }
    
    private static Scanner fileToScanner(File f)
            throws FileRelativeException {
        if (!f.exists()) {
            throw new FileRelativeException("File does not exist.");
        }
        
        if (f.isDirectory()) {
            throw new FileRelativeException("Path is a directory.");
        }
        
        Scanner result;
        try {
            result = new Scanner(f);
        }
        catch (FileNotFoundException fnfe) {
            throw new FileRelativeException("File does not exist.");
        }
        
        return result;
    }
    
    private static class Parameters {
        private final String myPackage;
        private final File myOutputPath;
        
        public Parameters(String packageSpec, File outputPath) {
            myPackage = packageSpec;
            myOutputPath = outputPath;
        }
        
        public String getPackage() {
            return myPackage;
        }
        
        public File getOutputPath() {
            return myOutputPath;
        }
    }
    
    private static class InputStuff {
        private final String myModelName;
        private final String myPackage;
        private final Object myModelDescriptor;
        
        public InputStuff(
                String modelName, String packageName, Object modelDescriptor) {
            myModelName = modelName;
            myPackage = packageName;
            myModelDescriptor = modelDescriptor;
        }
        
        public String getModelName() {
            return myModelName;
        }
        
        public String getPackage() {
            return myPackage;
        }
        
        public Object getModelDescriptor() {
            return myModelDescriptor;
        }
    }
    
    private static class FileRelativeException extends Exception {
        public FileRelativeException(String msg) {
            super(msg);
        }
    }
    
    private static class OperationException extends Exception {
        public OperationException(String msg) {
            super(msg);
        }
    }
    
    private static class Plan {
        private final Map<File, ModelOutputParameters> myModelsToProcess =
                new HashMap<>();
        
        public void addModel(
                File input, String packageSpec, File destinationPath) {
            myModelsToProcess.put(input,
                    new ModelOutputParameters(destinationPath, packageSpec));
        }
        
        public Iterable<File> files() {
            return myModelsToProcess.keySet();
        }
        
        public File getDestinationPath(File f) {
            return myModelsToProcess.get(f).getDestination();
        }
        
        public String getPackage(File f) {
            return myModelsToProcess.get(f).getPackage();
        }
    }
    
    private static class ProjectDescriptor {
        private final File myRoot;
        private final ImmutableList<File> mySourceDirectories;
        private final File myTargetDirectory;
        
        public ProjectDescriptor(File descriptorLocation,
                Iterable<File> sourceDirs, File targetDir) 
                throws OperationException {
            myRoot = descriptorLocation;
            mySourceDirectories = ImmutableList.copyOf(sourceDirs);
            myTargetDirectory = targetDir;
            
            for (int i = 0; i < mySourceDirectories.size(); i++) {
                for (int j = 0; j < mySourceDirectories.size(); j++) {
                    if (i != j) {
                        String iPath = mySourceDirectories.get(i).getPath();
                        String jPath = mySourceDirectories.get(j).getPath();
                        
                        if (iPath.startsWith(jPath)) {
                            throw new OperationException("Source directory "
                                    + "paths may not be prefixes of each "
                                    + "other, but " + jPath + " is a prefix of "
                                    + iPath +".");
                        }
                    }
                }
            }
        }
        
        public File getRoot() {
            return myRoot;
        }
        
        public ImmutableList<File> getSourceDirectories() {
            return mySourceDirectories;
        }
        
        public File getTargetDirectory() {
            return myTargetDirectory;
        }
        
        public String getPackage(File f) throws OperationException {
            String result = null;
            
            String fPath = f.getPath();
            Iterator<File> srcs = mySourceDirectories.iterator();
            while (result == null && srcs.hasNext()) {
                File src = srcs.next();
                String srcPath = src.getPath();
                if (fPath.startsWith(srcPath)) {
                    String relPath = fPath.substring(srcPath.length());
                    result = relPath.replace(File.pathSeparator, ".");
                }
            }
            
            if (result == null) {
                throw new OperationException("File " + f.getPath() 
                        + " does not exist on any specified source path.");
            }
            
            return result;
        }
    }
    
    private static class ModelOutputParameters {
        private final File myDestination;
        private final String myPackage;
        
        public ModelOutputParameters(File dest, String pkg) {
            myDestination = dest;
            myPackage = pkg;
        }
        
        public File getDestination() {
            return myDestination;
        }
        
        public String getPackage() {
            return myPackage;
        }
    }
    
    private static class FileException extends Exception {
        private final File myFile;
        private final String myFileTask;
        
        public FileException(File f, String task, String errorMsg) {
            super(errorMsg);
            
            myFile = f;
            myFileTask = task;
        }
        
        public File getFile() {
            return myFile;
        }
        
        public String getTask() {
            return myFileTask;
        }
    }
}
