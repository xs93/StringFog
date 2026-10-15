package com.github.megatronking.stringfog.plugin;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StringFogPluginFunctionalTest {

    private static final String RUNTIME_CLASSPATH_PROPERTY = "stringfog.test.runtime.classpath";
    private static final String AGP_CLASSPATH_PROPERTY = "stringfog.test.agp.classpath";
    private static final String REPOSITORY_PROPERTY = "stringfog.test.repository";

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Test
    public void isolatesConfigurationAcrossParallelAndroidModules() throws Exception {
        prepareFixture("multi-module");
        String[] tasks = {
                ":app:assembleDebug",
                ":library-base64:assembleDebug",
                ":library-bytes:assembleDebug"
        };
        BuildResult firstBuild = runner(tasks).build();

        assertNotNull(firstBuild.task(":app:assembleDebug"));
        assertEquals(TaskOutcome.SUCCESS, firstBuild.task(":app:assembleDebug").getOutcome());
        assertTrue(firstBuild.getOutput().contains("Configuration cache entry stored."));

        assertGeneratedSource(
                "app",
                "com/example/app",
                "String decrypt(String value, String key)"
        );
        assertGeneratedSource(
                "library-base64",
                "com/example/base64",
                "String decrypt(String value, String key)"
        );
        assertGeneratedSource(
                "library-bytes",
                "com/example/bytes/generated",
                "String decrypt(byte[] value, byte[] key)"
        );

        assertInstrumentedClass(
                "library-base64",
                "com/example/base64/Base64Secrets.class",
                "base64-library-secret",
                "com/example/base64/StringFog",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        );
        assertInstrumentedClass(
                "library-base64",
                "com/example/base64/KotlinSecrets.class",
                "kotlin-library-secret",
                "com/example/base64/StringFog",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        );
        assertInstrumentedClass(
                "library-bytes",
                "com/example/bytes/ByteSecrets.class",
                "bytes-library-secret",
                "com/example/bytes/generated/StringFog",
                "([B[B)Ljava/lang/String;"
        );

        BuildResult secondBuild = runner(tasks).build();
        assertTrue(secondBuild.getOutput().contains("Reusing configuration cache."));
    }

    @Test
    public void configuresAllAndroidModulesFromRootProject() throws Exception {
        prepareFixture("root-multi-module");
        String[] tasks = {
                ":app:assembleDebug",
                ":library-java:assembleDebug",
                ":library-kotlin:assembleDebug",
                ":excluded-library:assembleDebug"
        };
        BuildResult firstBuild = runner(tasks).build();

        assertNotNull(firstBuild.task(":app:assembleDebug"));
        assertEquals(TaskOutcome.SUCCESS, firstBuild.task(":app:assembleDebug").getOutcome());
        assertTrue(firstBuild.getOutput().contains("Configuration cache entry stored."));

        assertGeneratedSource(
                "app",
                "com/example/rootapp",
                "String decrypt(String value, String key)"
        );
        assertGeneratedSource(
                "library-java",
                "com/example/rootjava",
                "String decrypt(String value, String key)"
        );
        assertGeneratedSource(
                "library-kotlin",
                "com/example/rootkotlin",
                "String decrypt(String value, String key)"
        );
        assertNoGeneratedSource("excluded-library");

        assertInstrumentedClass(
                "library-java",
                "com/example/rootjava/RootJavaSecrets.class",
                "root-java-secret",
                "com/example/rootjava/StringFog",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        );
        assertInstrumentedClass(
                "library-kotlin",
                "com/example/rootkotlin/RootKotlinSecrets.class",
                "root-kotlin-secret",
                "com/example/rootkotlin/StringFog",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        );
        assertUninstrumentedClass(
                "excluded-library",
                "com/example/excluded/ExcludedSecrets.class",
                "excluded-library-secret"
        );

        BuildResult secondBuild = runner(tasks).build();
        assertTrue(secondBuild.getOutput().contains("Reusing configuration cache."));
    }

    private GradleRunner runner(String... tasks) throws IOException {
        String runtimeClasspath = requireSystemProperty(RUNTIME_CLASSPATH_PROPERTY);
        String agpClasspath = requireSystemProperty(AGP_CLASSPATH_PROPERTY);
        List<String> arguments = new ArrayList<>();
        arguments.add("--parallel");
        arguments.add("--configuration-cache");
        arguments.add("--configuration-cache-problems=fail");
        arguments.add("-D" + RUNTIME_CLASSPATH_PROPERTY + "=" + runtimeClasspath);
        arguments.add("-D" + REPOSITORY_PROPERTY + "="
                + testProjectDir.getRoot().toPath().resolve("maven-repository"));
        arguments.add("--stacktrace");
        for (String task : tasks) {
            arguments.add(task);
        }

        return GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withGradleVersion("9.6.0")
                .withPluginClasspath(pluginClasspath(runtimeClasspath, agpClasspath))
                .withArguments(arguments);
    }

    private List<File> pluginClasspath(String runtimeClasspath, String agpClasspath)
            throws IOException {
        Properties metadata = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("plugin-under-test-metadata.properties")) {
            assertNotNull("Missing plugin-under-test metadata", input);
            metadata.load(input);
        }

        Set<File> classpath = new LinkedHashSet<>();
        String implementationClasspath = metadata.getProperty("implementation-classpath");
        for (String path : implementationClasspath.split(Pattern.quote(File.pathSeparator))) {
            classpath.add(new File(path));
        }
        for (String path : runtimeClasspath.split(Pattern.quote(File.pathSeparator))) {
            classpath.add(new File(path));
        }
        for (String path : agpClasspath.split(Pattern.quote(File.pathSeparator))) {
            classpath.add(new File(path));
        }
        return new ArrayList<>(classpath);
    }

    private void assertGeneratedSource(String module, String packagePath, String signature)
            throws IOException {
        Path source = testProjectDir.getRoot().toPath()
                .resolve(module)
                .resolve("build/generated/source/stringFog/debug")
                .resolve(packagePath)
                .resolve("StringFog.java");
        assertTrue("Missing generated source " + source, Files.isRegularFile(source));
        assertTrue(Files.readString(source).contains(signature));
    }

    private void assertNoGeneratedSource(String module) {
        Path generatedSources = testProjectDir.getRoot().toPath()
                .resolve(module)
                .resolve("build/generated/source/stringFog");
        assertFalse("Excluded module generated StringFog sources", Files.exists(generatedSources));
    }

    private void assertInstrumentedClass(
            String module,
            String classEntry,
            String plaintext,
            String expectedOwner,
            String expectedDescriptor
    ) throws IOException {
        Path aar = testProjectDir.getRoot().toPath()
                .resolve(module)
                .resolve("build/outputs/aar")
                .resolve(module + "-debug.aar");
        byte[] classBytes = readClassFromAar(aar, classEntry);
        InspectionVisitor visitor = new InspectionVisitor(plaintext, expectedOwner, expectedDescriptor);
        new ClassReader(classBytes).accept(visitor, 0);
        assertFalse("Plaintext remains in " + classEntry, visitor.plaintextFound);
        assertTrue("Expected decrypt call missing from " + classEntry, visitor.decryptCallFound);
    }

    private void assertUninstrumentedClass(
            String module,
            String classEntry,
            String plaintext
    ) throws IOException {
        Path aar = testProjectDir.getRoot().toPath()
                .resolve(module)
                .resolve("build/outputs/aar")
                .resolve(module + "-debug.aar");
        byte[] classBytes = readClassFromAar(aar, classEntry);
        InspectionVisitor visitor = new InspectionVisitor(plaintext, "", "");
        new ClassReader(classBytes).accept(visitor, 0);
        assertTrue("Plaintext missing from excluded module " + classEntry, visitor.plaintextFound);
        assertFalse("Excluded module contains a decrypt call " + classEntry, visitor.decryptCallFound);
    }

    private byte[] readClassFromAar(Path aarPath, String classEntry) throws IOException {
        try (ZipFile aar = new ZipFile(aarPath.toFile())) {
            ZipEntry classesJar = aar.getEntry("classes.jar");
            assertNotNull("Missing classes.jar in " + aarPath, classesJar);
            try (JarInputStream jar = new JarInputStream(aar.getInputStream(classesJar))) {
                JarEntry entry;
                while ((entry = jar.getNextJarEntry()) != null) {
                    if (classEntry.equals(entry.getName())) {
                        return jar.readAllBytes();
                    }
                }
            }
        }
        throw new AssertionError("Missing " + classEntry + " in " + aarPath);
    }

    private void prepareFixture(String fixtureName) throws Exception {
        copyFixture(fixtureName);
        configureAndroidSdk();
        createRuntimeRepository();
    }

    private void copyFixture(String fixtureName) throws IOException, URISyntaxException {
        Path fixture = Paths.get(Objects.requireNonNull(
                getClass().getResource("/fixtures/" + fixtureName)
        ).toURI());
        Path target = testProjectDir.getRoot().toPath();
        try (java.util.stream.Stream<Path> paths = Files.walk(fixture)) {
            paths.forEach(source -> {
                Path destination = target.resolve(fixture.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new FixtureCopyException(e);
                }
            });
        } catch (FixtureCopyException e) {
            throw e.ioException;
        }
    }

    private void createRuntimeRepository() throws IOException {
        String runtimeClasspath = requireSystemProperty(RUNTIME_CLASSPATH_PROPERTY);
        File interfaceJar = null;
        File xorJar = null;
        for (String path : runtimeClasspath.split(Pattern.quote(File.pathSeparator))) {
            File file = new File(path);
            if ("stringfog-interface.jar".equals(file.getName())) {
                interfaceJar = file;
            } else if ("xor.jar".equals(file.getName())) {
                xorJar = file;
            }
        }
        assertNotNull("Missing local interface JAR", interfaceJar);
        assertNotNull("Missing local xor JAR", xorJar);

        installMavenArtifact("interface", interfaceJar, "");
        installMavenArtifact(
                "xor",
                xorJar,
                "<dependencies><dependency>"
                        + "<groupId>com.github.megatronking.stringfog</groupId>"
                        + "<artifactId>interface</artifactId><version>6.0.0</version>"
                        + "</dependency></dependencies>"
        );
    }

    private void installMavenArtifact(String artifactId, File jar, String dependencies)
            throws IOException {
        Path artifactDirectory = testProjectDir.getRoot().toPath()
                .resolve("maven-repository/com/github/megatronking/stringfog")
                .resolve(artifactId)
                .resolve("6.0.0");
        Files.createDirectories(artifactDirectory);
        Files.copy(
                jar.toPath(),
                artifactDirectory.resolve(artifactId + "-6.0.0.jar"),
                StandardCopyOption.REPLACE_EXISTING
        );
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>com.github.megatronking.stringfog</groupId>"
                + "<artifactId>" + artifactId + "</artifactId>"
                + "<version>6.0.0</version>" + dependencies + "</project>";
        Files.writeString(artifactDirectory.resolve(artifactId + "-6.0.0.pom"), pom);
    }

    private void configureAndroidSdk() throws IOException {
        String sdkDir = System.getenv("ANDROID_HOME");
        if (sdkDir == null || sdkDir.isEmpty()) {
            sdkDir = System.getenv("ANDROID_SDK_ROOT");
        }
        if (sdkDir == null || sdkDir.isEmpty()) {
            Path current = Paths.get(System.getProperty("user.dir"));
            Path localProperties = null;
            while (current != null) {
                Path candidate = current.resolve("local.properties");
                if (Files.isRegularFile(candidate)) {
                    localProperties = candidate;
                    break;
                }
                current = current.getParent();
            }
            assertNotNull("Unable to locate local.properties", localProperties);
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(localProperties)) {
                properties.load(input);
            }
            sdkDir = properties.getProperty("sdk.dir");
        }
        assertNotNull("Android SDK path is not configured", sdkDir);

        Properties properties = new Properties();
        properties.setProperty("sdk.dir", sdkDir);
        try (java.io.OutputStream output = Files.newOutputStream(
                testProjectDir.getRoot().toPath().resolve("local.properties"))) {
            properties.store(output, null);
        }
    }

    private String requireSystemProperty(String name) {
        String value = System.getProperty(name);
        assertNotNull("Missing system property " + name, value);
        return value;
    }

    private static final class FixtureCopyException extends RuntimeException {
        private final IOException ioException;

        private FixtureCopyException(IOException ioException) {
            this.ioException = ioException;
        }
    }

    private static final class InspectionVisitor extends ClassVisitor {
        private final String plaintext;
        private final String expectedOwner;
        private final String expectedDescriptor;
        private boolean plaintextFound;
        private boolean decryptCallFound;

        private InspectionVisitor(
                String plaintext,
                String expectedOwner,
                String expectedDescriptor
        ) {
            super(Opcodes.ASM9);
            this.plaintext = plaintext;
            this.expectedOwner = expectedOwner;
            this.expectedDescriptor = expectedDescriptor;
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitLdcInsn(Object value) {
                    if (plaintext.equals(value)) {
                        plaintextFound = true;
                    }
                }

                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface
                ) {
                    if (opcode == Opcodes.INVOKESTATIC
                            && expectedOwner.equals(owner)
                            && "decrypt".equals(name)
                            && expectedDescriptor.equals(descriptor)) {
                        decryptCallFound = true;
                    }
                }
            };
        }
    }
}
