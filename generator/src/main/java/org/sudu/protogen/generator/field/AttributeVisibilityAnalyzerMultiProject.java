package org.sudu.protogen.generator.field;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class AttributeVisibilityAnalyzerMultiProject {

    /**
     * Auto-discover Java projects in a workspace folder
     */
    public static Set<String> discoverJavaProjects(String workspacePath) {
        Set<String> projectPaths = new HashSet<>();
        File workspace = new File(workspacePath);

        if (!workspace.isDirectory()) {
            System.err.println("Workspace path is not a directory: " + workspacePath);
            return projectPaths;
        }

        File[] projects = workspace.listFiles();
        if (projects == null) return projectPaths;

        for (File project : projects) {
            if (project.isDirectory()) {
                // Check common Java project structures
                File srcMain = new File(project, "src/main/java");        // Maven structure
                File src = new File(project, "src");                      // Eclipse structure

                if (srcMain.exists() && srcMain.isDirectory()) {
                    projectPaths.add(srcMain.getAbsolutePath());
                    System.out.println("Found Maven project: " + srcMain.getAbsolutePath());
                } else if (src.exists() && src.isDirectory() && containsJavaFiles(src)) {
                    projectPaths.add(src.getAbsolutePath());
                    System.out.println("Found Eclipse project: " + src.getAbsolutePath());
                }
            }
        }

        return projectPaths;
    }

    /**
     * Check if a directory contains Java files (directly or in subdirectories)
     */
    private static boolean containsJavaFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".java")) {
                return true;
            } else if (file.isDirectory() && containsJavaFiles(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Main method with multiple project paths
     */
    public static boolean needGetter(String className, String attributeName, Set<String> projectPaths) throws Exception {
        File classFile = findJavaFileForClass(className, projectPaths);
        if (classFile == null) {
            throw new Exception("Class file not found for class: " + className + " in any of the provided project paths");
        }

        return analyzeClassRecursive(classFile, className, attributeName, projectPaths, false);
    }

    /**
     * Backward compatibility method for single path
     */
    public static boolean needGetter(String className, String attributeName, String singlePath) throws Exception {
        Set<String> paths = new HashSet<>();
        paths.add(singlePath);
        return needGetter(className, attributeName, paths);
    }

    private static boolean analyzeClassRecursive(File file, String className, String attributeName, Set<String> projectPaths, boolean isInSuperclass) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        Optional<ClassOrInterfaceDeclaration> clazzOpt = cu.getClassByName(className);
        if (clazzOpt.isEmpty()) {
            throw new Exception("Class not found in file: " + file.getName());
        }

        ClassOrInterfaceDeclaration clazz = clazzOpt.get();

        // Check if the attribute is declared in this class
        for (FieldDeclaration field : clazz.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                if (var.getNameAsString().equals(attributeName)) {
                    if (!isInSuperclass) {
                        return false; // Declared in same class, no getter needed
                    } else {
                        // Inherited field - check visibility
                        return field.isPrivate() || field.isProtected();
                    }
                }
            }
        }

        // Check superclass if exists
        if (clazz.getExtendedTypes().isNonEmpty()) {
            ClassOrInterfaceType superClassType = clazz.getExtendedTypes(0);
            String superClassName = superClassType.getNameAsString();

            // Find superclass file using optimized multi-project approach
            File superClassFile = findSuperClassFile(cu, superClassName, file.getParentFile(), projectPaths);

            if (superClassFile != null) {
                return analyzeClassRecursive(superClassFile, superClassName, attributeName, projectPaths, true);
            } else {
                // Fallback to reflection for system classes
                return handleReflectionFallback(superClassName, attributeName);
            }
        }

        return false; // Attribute not found in hierarchy
    }

    /**
     * Optimized method to find superclass file using import statements across multiple projects
     */
    private static File findSuperClassFile(CompilationUnit cu, String superClassName, File currentClassDir, Set<String> projectPaths) {
        // Build import map for quick lookup
        Map<String, String> importMap = buildImportMap(cu);

        // Get current package
        String currentPackage = getCurrentPackage(cu);

        // Try to find in each project path
        for (String projectPath : projectPaths) {
            String superClassPath = determineSuperClassPath(superClassName, importMap, currentPackage, currentClassDir, projectPath);

            if (superClassPath != null) {
                File superClassFile = new File(superClassPath);
                if (superClassFile.exists()) {
                    return superClassFile;
                }
            }
        }

        return null;
    }

    /**
     * Build a map of class names to their full import paths
     */
    private static Map<String, String> buildImportMap(CompilationUnit cu) {
        Map<String, String> importMap = new HashMap<>();

        for (ImportDeclaration importDecl : cu.getImports()) {
            if (!importDecl.isAsterisk()) {
                String fullImport = importDecl.getNameAsString();
                String className = getClassNameFromImport(fullImport);
                importMap.put(className, fullImport);
            }
        }

        return importMap;
    }

    /**
     * Get current package name from CompilationUnit
     */
    private static String getCurrentPackage(CompilationUnit cu) {
        Optional<PackageDeclaration> packageDecl = cu.getPackageDeclaration();
        return packageDecl.map(pkg -> pkg.getNameAsString()).orElse("");
    }

    /**
     * Determine the file path for the superclass within a specific project
     */
    private static String determineSuperClassPath(String superClassName, Map<String, String> importMap,
                                                  String currentPackage, File currentClassDir, String projectPath) {

        // Case 1: Explicit import exists
        if (importMap.containsKey(superClassName)) {
            String fullImport = importMap.get(superClassName);
            return convertImportToFilePath(fullImport, projectPath);
        }

        // Case 2: Same package (no import needed)
        if (!currentPackage.isEmpty()) {
            String samePkgPath = convertPackageToFilePath(currentPackage, superClassName, projectPath);
            File samePkgFile = new File(samePkgPath);
            if (samePkgFile.exists()) {
                return samePkgPath;
            }
        }

        // Case 3: Same directory as current class (but ensure it's within the project)
        File sameDir = new File(currentClassDir, superClassName + ".java");
        if (sameDir.exists() && sameDir.getAbsolutePath().startsWith(projectPath)) {
            return sameDir.getAbsolutePath();
        }

        // Case 4: Check if it's a fully qualified name in extends clause
        if (superClassName.contains(".")) {
            return convertImportToFilePath(superClassName, projectPath);
        }

        return null;
    }

    /**
     * Convert import statement to file path within a project
     */
    private static String convertImportToFilePath(String fullImport, String projectPath) {
        String relativePath = fullImport.replace(".", File.separator) + ".java";
        return new File(projectPath, relativePath).getAbsolutePath();
    }

    /**
     * Convert package name to file path for a class within a project
     */
    private static String convertPackageToFilePath(String packageName, String className, String projectPath) {
        String packagePath = packageName.replace(".", File.separator);
        String relativePath = packagePath + File.separator + className + ".java";
        return new File(projectPath, relativePath).getAbsolutePath();
    }

    /**
     * Extract class name from full import path
     */
    private static String getClassNameFromImport(String fullImport) {
        int lastDot = fullImport.lastIndexOf('.');
        return (lastDot >= 0) ? fullImport.substring(lastDot + 1) : fullImport;
    }

    /**
     * Handle reflection fallback for system classes
     */
    private static boolean handleReflectionFallback(String superClassName, String attributeName) {
        try {
            Class<?> superClass = Class.forName(superClassName);
            Field field = findFieldReflectively(superClass, attributeName);
            if (field != null) {
                int modifiers = field.getModifiers();
                return Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Could not load superclass by reflection: " + superClassName);
        }
        return false;
    }

    /**
     * Find field using reflection across class hierarchy
     */
    private static Field findFieldReflectively(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Find Java file across multiple project paths
     */
    private static File findJavaFileForClass(String className, Set<String> projectPaths) {
        for (String projectPath : projectPaths) {
            File result = findJavaFileForClass(className, new File(projectPath));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Find Java file within a single project path (recursive)
     */
    private static File findJavaFileForClass(String className, File folder) {
        if (!folder.isDirectory()) return null;

        File[] files = folder.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                File result = findJavaFileForClass(className, file);
                if (result != null) return result;
            } else if (file.getName().equals(className + ".java")) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    Optional<ClassOrInterfaceDeclaration> clazz = cu.getClassByName(className);
                    if (clazz.isPresent()) {
                        return file;
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        }
        return null;
    }

    // Main class, just to show case how to use this utility class
    public static void main(String[] args) {
        try {
            Set<String> projectPaths = new HashSet<>();
            projectPaths.add("C:\\Users\\bilal.belli\\eclipse-workspace\\ecompta\\ecompta\\serveur\\src\\main\\java");
            // Add others
            boolean result = AttributeVisibilityAnalyzerMultiProject.needGetter("MyClassHere", "myAttributeExampleHere", projectPaths);
            System.out.println("Need getter? " + result);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}