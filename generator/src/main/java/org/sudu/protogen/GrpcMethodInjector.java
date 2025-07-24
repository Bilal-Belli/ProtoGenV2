package org.sudu.protogen;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

public class GrpcMethodInjector {
    private static final Path FIRST_FOLDER = Paths.get("C:\\Users\\bilal.belli\\eclipse-workspace-protogen\\protogen-source-code\\tests\\build\\generated\\source\\proto\\test\\protogen");
    // Array of directories to search through in order
    private static final Path[] SEARCH_DIRECTORIES = {
            Paths.get("C:\\Users\\bilal.belli\\eclipse-workspace\\myProject\\src\\main\\java"), // 
            // Add more directories as needed
    };

    public static void main(String[] args) throws IOException {
        JavaParser parser = new JavaParser();
        // 1. Parse each file in the first folder
        try (Stream<Path> paths = Files.walk(FIRST_FOLDER)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(firstFile -> {
                try {
                    CompilationUnit cu = parser.parse(firstFile).getResult().orElseThrow();
                    Optional<MethodDeclaration> toGrpcMethod = cu.findAll(MethodDeclaration.class).stream()
                            .filter(m -> m.getNameAsString().equals("toGrpc"))
                            .findFirst();

                    if (toGrpcMethod.isEmpty()) {
                        System.out.println("No toGrpc method in: " + firstFile);
                        return;
                    }

                    // 2. Search for file with same name in multiple directories
                    String fileName = firstFile.getFileName().toString();
                    Optional<Path> matchingFile = findFileInDirectories(fileName);
                    if (matchingFile.isPresent()) {
                        Path matchFile = matchingFile.get();
                        CompilationUnit targetCu = parser.parse(matchFile).getResult().orElseThrow();

                        // 3. Add the NotNull import if not already present
                        addImportIfNotExists(targetCu, "jakarta.validation.constraints.NotNull");

                        // 4. Inject method into the matching file
                        targetCu.getTypes().get(0).addMember(toGrpcMethod.get());
                        Files.writeString(matchFile, targetCu.toString());
                        System.out.println("Appended toGrpc to: " + matchFile);
                    } else {
                        System.out.println("No matching file found for: " + fileName + " in any of the search directories");
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + firstFile + ": " + e.getMessage());
                }
            });
        }
    }

    /**
     * Searches for a file with the given name in the configured directories in order.
     * Returns the first match found, or empty if no match is found in any directory.
     */
    private static Optional<Path> findFileInDirectories(String fileName) {
        for (int i = 0; i < SEARCH_DIRECTORIES.length; i++) {
            Path searchDir = SEARCH_DIRECTORIES[i];
            System.out.println("Searching in directory " + (i + 1) + ": " + searchDir);
            try (Stream<Path> files = Files.walk(searchDir)) {
                Optional<Path> match = files
                        .filter(p -> p.toString().endsWith(fileName))
                        .findFirst();
                if (match.isPresent()) {
                    System.out.println("File found in directory " + (i + 1) + ": " + match.get());
                    return match;
                }
            } catch (IOException e) {
                System.err.println("Error searching in directory " + searchDir + ": " + e.getMessage());
                // Continue searching in the next directory
            }
        }
        return Optional.empty();
    }

    /**
     * Adds an import declaration to the CompilationUnit if it doesn't already exist.
     */
    private static void addImportIfNotExists(CompilationUnit cu, String importName) {
        boolean importExists = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importName));
        if (!importExists) {
            cu.addImport(importName);
        }
    }
}