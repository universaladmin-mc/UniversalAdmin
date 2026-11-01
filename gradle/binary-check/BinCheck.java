import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;

/**
 * Binary compatibility check, run by the Gradle task {@code binaryCompatibility}: every class, method and field the
 * plugin's own classes ({@code dev/universaladmin/}) refer to in the server's API (org.bukkit, io.papermc, net.kyori,
 * ...) must exist in a given API classpath - the oldest or newest supported Paper version. Compiling against one
 * version does not prove this for the others: a method whose return type changed between Adventure 4 (Paper 1.21) and
 * Adventure 5 (Paper 26) compiles fine and fails with NoSuchMethodError on the other version.
 *
 * <p>Usage: {@code java BinCheck.java <plugin.jar> <file listing the API classpath jars, one per line>}. Needs Java 25
 * (java.lang.classfile); exits with 1 if anything does not resolve.
 */
public class BinCheck {

    static final List<String> CHECKED = List.of("org/bukkit/", "io/papermc/", "com/destroystokyo/", "net/kyori/",
            "net/md_5/", "org/spigotmc/", "com/mojang/brigadier/", "com/google/gson/", "com/google/common/", "org/slf4j/",
            "org/apache/logging/", "org/jetbrains/annotations/", "it/unimi/");

    static final Map<String, byte[]> api = new HashMap<>();
    static final Map<String, Optional<ClassModel>> parsed = new HashMap<>();

    public static void main(String[] args) throws Exception {
        Path pluginJar = Path.of(args[0]);
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            if (line.isBlank() || !line.endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(line.trim())) {
                jar.stream().filter(e -> e.getName().endsWith(".class")).forEach(e -> {
                    try {
                        api.putIfAbsent(e.getName().substring(0, e.getName().length() - 6), jar.getInputStream(e).readAllBytes());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
        }
        Set<String> own = new TreeSet<>();
        List<ClassModel> ownModels = new ArrayList<>();
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            for (var e : jar.stream().toList()) {
                if (e.getName().endsWith(".class") && !e.getName().contains("META-INF")) {
                    ClassModel model = ClassFile.of().parse(jar.getInputStream(e).readAllBytes());
                    own.add(model.thisClass().asInternalName());
                    ownModels.add(model);
                }
            }
        }
        Map<String, Set<String>> problems = new TreeMap<>();
        for (ClassModel model : ownModels) {
            String user = model.thisClass().asInternalName();
            if (!user.startsWith("dev/universaladmin/")) {
                continue; // shaded libraries are not ours to fix
            }
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof ClassEntry ce) {
                    String name = element(ce.asInternalName());
                    if (checked(name) && !own.contains(name) && !api.containsKey(name)) {
                        problems.computeIfAbsent("missing class " + name, k -> new TreeSet<>()).add(user);
                    }
                } else if (entry instanceof MemberRefEntry ref) {
                    String owner = element(ref.owner().asInternalName());
                    if (!checked(owner) || own.contains(owner) || owner.startsWith("[")) {
                        continue;
                    }
                    if (!api.containsKey(owner)) {
                        continue; // reported as missing class
                    }
                    String name = ref.name().stringValue();
                    String type = ref.type().stringValue();
                    boolean field = ref instanceof FieldRefEntry;
                    if (!resolves(owner, name, type, field, new TreeSet<>())) {
                        String kind = field ? "field " : (ref instanceof InterfaceMethodRefEntry ? "imethod " : "method ");
                        problems.computeIfAbsent(kind + owner + "." + name + type, k -> new TreeSet<>()).add(user);
                    }
                }
            }
        }
        System.out.println(problems.size() + " binary compatibility problem(s) against " + Path.of(args[1]).getFileName());
        problems.forEach((problem, users) -> System.out.println("  " + problem + "\n      used by " + users));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    static String element(String name) {
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return name;
    }

    static boolean checked(String name) {
        return CHECKED.stream().anyMatch(name::startsWith);
    }

    static Optional<ClassModel> model(String name) {
        return parsed.computeIfAbsent(name, n -> {
            byte[] bytes = api.get(n);
            if (bytes == null) {
                // JDK classes come from the running JDK (the plugin is compiled with --release 21 against it).
                try (var in = ClassLoader.getSystemResourceAsStream(n + ".class")) {
                    bytes = in == null ? null : in.readAllBytes();
                } catch (java.io.IOException e) {
                    bytes = null;
                }
            }
            return bytes == null ? Optional.empty() : Optional.of(ClassFile.of().parse(bytes));
        });
    }

    /** Looks the member up in the class, its superclasses and interfaces; a JDK supertype counts as resolved. */
    static boolean resolves(String owner, String name, String type, boolean field, Set<String> seen) {
        if (!seen.add(owner)) {
            return false;
        }
        Optional<ClassModel> model = model(owner);
        if (model.isEmpty()) {
            return !checked(owner) && !owner.startsWith("java/"); // a library outside the checked packages
        }
        ClassModel m = model.get();
        if (field) {
            for (FieldModel f : m.fields()) {
                if (f.fieldName().stringValue().equals(name) && f.fieldType().stringValue().equals(type)) {
                    return true;
                }
            }
        } else {
            for (MethodModel method : m.methods()) {
                if (method.methodName().stringValue().equals(name) && method.methodType().stringValue().equals(type)) {
                    return true;
                }
            }
        }
        if (m.superclass().isPresent() && resolves(m.superclass().get().asInternalName(), name, type, field, seen)) {
            return true;
        }
        for (ClassEntry iface : m.interfaces()) {
            if (resolves(iface.asInternalName(), name, type, field, seen)) {
                return true;
            }
        }
        return false;
    }
}
