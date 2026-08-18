package xyz.baaad.machikado;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class Utils {
    public static class FileMapping {
        public TreeMap<String, String> map = new TreeMap<>();
        public FileMapping() {}
        public void insert(String targetPath, String sourcePath) {
            map.put(targetPath, sourcePath);
        }
        public int len() { return map.size(); }
        public boolean isEmpty() { return map.isEmpty(); }
        public Set<Map.Entry<String, String>> entrySet() { return map.entrySet(); }
        public boolean containsValue(String value) { return map.containsValue(value); }
        public boolean containsKey(String key) { return map.containsKey(key); }
    }

    public static List<Sign.FileEntry> loadFolderFiles(Path folder, List<String> ignorePrefixes, List<String> ignoreNames, FileMapping mapping) throws IOException {
        List<Sign.FileEntry> entries = new ArrayList<>();

        if (mapping != null) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String targetPath = entry.getKey();
                String sourcePath = entry.getValue();
                Path fullSource = folder.resolve(sourcePath);
                byte[] content;
                try {
                    content = Files.readAllBytes(fullSource);
                } catch (IOException e) {
                    throw new IOException(String.format("failed to read mapped source '%s' (-> target '%s'): %s", sourcePath, targetPath, e.getMessage()), e);
                }
                entries.add(new Sign.FileEntry(targetPath, content));
            }
        }

        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Path relPathObj = folder.relativize(path);
                            String relativePath = relPathObj.toString().replace('\\', '/');

                            if (mapping != null) {
                                if (mapping.containsValue(relativePath) || mapping.containsKey(relativePath)) {
                                    return;
                                }
                            }

                            if (ignorePrefixes != null) {
                                for (String p : ignorePrefixes) {
                                    if (relativePath.startsWith(p)) return;
                                }
                            }

                            if (ignoreNames != null) {
                                for (String n : ignoreNames) {
                                    if (relativePath.equals(n)) return;
                                }
                            }

                            byte[] content = Files.readAllBytes(path);
                            entries.add(new Sign.FileEntry(relativePath, content));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        entries.sort(Comparator.comparing(e -> e.relativePath));
        return entries;
    }

    public static List<Sign.FileEntry> loadFolderFiles(String folder, String[] ignorePrefixes, String[] ignoreNames, FileMapping mapping) throws IOException {
        return loadFolderFiles(Paths.get(folder),
                ignorePrefixes == null ? null : Arrays.asList(ignorePrefixes),
                ignoreNames == null ? null : Arrays.asList(ignoreNames),
                mapping);
    }
}
