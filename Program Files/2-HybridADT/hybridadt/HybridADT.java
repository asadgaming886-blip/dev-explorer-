package hybridadt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * HybridADT - single-file ADT implementation containing:
 * - Iterative Deepening DFS directory indexing (IDDFS)
 * - Filename Trie index (prefix search)
 * - Inverted content index (token -> (path -> frequency))
 * - Simple Java-serialization persistence
 * - Single background worker thread for filesystem events
 *
 * Note:
 * - FileMetadata and FileEvent are expected to be available in package hybridadt
 *   (FileMetadata implements Serializable).
 * - Binary files are indexed for metadata/trie but skipped for content tokenization.
 */
public class HybridADT implements HybridADTInterface {

    /* --------------------
       Configuration
       -------------------- */
    private static final int MAX_IDDFS_DEPTH = 256; // safety cap for IDDFS
    private static final Set<String> TEXT_EXTS = Set.of(
            "txt", "md", "java", "py", "c", "cpp", "h", "json", "xml", "html", "css", "js", "properties", "yaml", "yml", "csv"
    );

    /* --------------------
       Runtime state
       -------------------- */

    private volatile String currentPath;
    private volatile boolean codeModeEnabled = false;

    // main metadata index: absolutePath -> metadata
    private final ConcurrentMap<String, FileMetadata> metadataMap = new ConcurrentHashMap<>();

    // ignore rules (simple substring matching)
    private final Set<String> ignoreRules = ConcurrentHashMap.newKeySet();

    /* --------------------
       Data structures (internal)
       -------------------- */

    // Trie for filename prefix search
    private final Trie trie = new Trie();

    // Inverted index: token -> (path -> freq)
    private final InvertedIndex inverted = new InvertedIndex();

    /* --------------------
       Background worker
       -------------------- */

    private final BlockingQueue<FileEvent> eventQueue = new LinkedBlockingQueue<>();
    private Thread workerThread = null;
    private volatile boolean workerRunning = false;

    /* --------------------
       Persistence
       -------------------- */

    private final Path persistenceFile = Paths.get("devexplorer_index.dat");

    /* --------------------
       Constructor
       -------------------- */

    public HybridADT() {
        this.currentPath = System.getProperty("user.home");
    }

    /* =========================
       Navigation Operations (6)
       ========================= */

    @Override
    public void openDirectory(String path) {
        if (path == null || path.isBlank()) return;
        currentPath = path;
    }

    @Override
    public void navigateToParent() {
        if (currentPath == null || currentPath.isEmpty()) return;
        String sep = FileSystems.getDefault().getSeparator();
        int last = currentPath.lastIndexOf(sep);
        if (last > 0) currentPath = currentPath.substring(0, last);
    }

    @Override
    public void navigateToPath(String path) {
        openDirectory(path);
    }

    @Override
    public String getCurrentPath() {
        return currentPath;
    }

    @Override
    public List<FileMetadata> listCurrentDirectory() {
        try {
            Path dir = Paths.get(currentPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return Collections.emptyList();
            List<FileMetadata> out = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    if (shouldIgnore(p)) continue;
                    FileMetadata m = buildMetadata(p);
                    if (m != null) out.add(m);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public void refreshCurrentDirectory() {
        // re-index entries in current directory (shallow)
        try {
            Path dir = Paths.get(currentPath);
            if (!Files.isDirectory(dir)) return;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    if (shouldIgnore(p)) continue;
                    indexPath(p);
                }
            }
        } catch (Exception ignored) {}
    }

    /* =========================
       Search Operations (6)
       ========================= */

    @Override
    public List<FileMetadata> searchByFilenamePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        List<String> paths = trie.searchByPrefix(prefix.toLowerCase(Locale.ROOT));
        return toMetadataList(paths);
    }

    @Override
    public List<FileMetadata> searchByFileContent(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        Set<String> results = inverted.search(query);
        return toMetadataList(results);
    }

    @Override
    public List<FileMetadata> searchInCurrentDirectory(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        Set<String> hits = inverted.search(query);
        List<String> filtered = new ArrayList<>();
        String cp = Paths.get(currentPath).toAbsolutePath().toString();
        for (String p : hits) {
            if (p.startsWith(cp)) filtered.add(p);
        }
        return toMetadataList(filtered);
    }

    @Override
    public List<FileMetadata> filterByExtension(String extension) {
        if (extension == null) return Collections.emptyList();
        String ext = extension.startsWith(".") ? extension.toLowerCase(Locale.ROOT) : "." + extension.toLowerCase(Locale.ROOT);
        List<FileMetadata> out = new ArrayList<>();
        for (FileMetadata m : metadataMap.values()) {
            if (m.extension() != null && m.extension().equalsIgnoreCase(ext)) out.add(m);
        }
        return out;
    }

    @Override
    public List<FileMetadata> rankSearchResults(List<FileMetadata> results) {
        if (results == null || results.isEmpty()) return Collections.emptyList();
        // Score using inverted index frequencies: sum frequencies of tokens present in the query if available.
        // For simplicity, rank by lastModified desc as fallback; here we perform a basic heuristic:
        results.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? 1 : -1;
            return Long.compare(b.lastModified(), a.lastModified());
        });
        return results;
    }

    @Override
    public void clearSearch() {
        // nothing to clear in the ADT core (stateless search)
    }

    /* =========================
       Indexing Operations (7)
       ========================= */

    @Override
    public void buildInitialIndex(String rootPath)
    {
        if (rootPath == null || rootPath.isBlank()) return;
        // IDDFS up to MAX_IDDFS_DEPTH
        Path root = Paths.get(rootPath);
        if (!Files.exists(root)) return;

        metadataMap.clear();
        trie.clear();
        inverted.clear();

        int depthLimit = 0;
        long prevCount = -1L;

        while (depthLimit <= MAX_IDDFS_DEPTH)
        {
            iddfsDepthLimited(root, depthLimit);
            long currentCount = metadataMap.size();
            if (currentCount == prevCount && depthLimit > 0)
            {
                // no new nodes found at this larger depth -> assume complete for practical purposes
                break;
            }
            prevCount = currentCount;
            depthLimit++;
        }
    }

    @Override
    public void updateIndexOnFileCreate(String filePath)
    {
        if (filePath == null) return;
        indexPathSilently(Paths.get(filePath));
    }

    @Override
    public void updateIndexOnFileModify(String filePath)
    {
        if (filePath == null) return;
        indexPathSilently(Paths.get(filePath));
    }

    @Override
    public void updateIndexOnFileDelete(String filePath)
    {
        if (filePath == null) return;
        String key = Paths.get(filePath).toAbsolutePath().toString();
        metadataMap.remove(key);
        trie.removePath(key);
        inverted.removePath(key);
    }

    @Override
    public void reindexDirectory(String path)
    {
        if (path == null) return;
        Path dir = Paths.get(path);
        if (!Files.isDirectory(dir)) return;
        // remove entries under this dir
        List<String> toRemove = new ArrayList<>();
        String prefix = dir.toAbsolutePath().toString();
        for (String p : metadataMap.keySet()) {if (p.startsWith(prefix)) toRemove.add(p);}
        for (String r : toRemove)
        {
            metadataMap.remove(r);
            trie.removePath(r);
            inverted.removePath(r);
        }
        // index subtree (use IDDFS shallow-first with limited depth growth)
        int depthLimit = 0;
        while (depthLimit <= MAX_IDDFS_DEPTH)
        {
            iddfsDepthLimited(dir, depthLimit);
            depthLimit++;
        }
    }

    @Override
    public void loadIndexFromDisk()
    {
        IndexPersistence.DataBlob blob = IndexPersistence.load(persistenceFile);
        if (blob == null) return;
        metadataMap.clear();
        metadataMap.putAll(blob.metadataMap);
        inverted.loadFromMap(blob.invertedMap);
        trie.clear();
        trie.buildFromPaths(metadataMap.keySet());
    }

    @Override
    public void persistIndexToDisk()
    {
        IndexPersistence.DataBlob blob = new IndexPersistence.DataBlob();
        blob.metadataMap = new HashMap<>(metadataMap);
        blob.invertedMap = inverted.snapshot();
        IndexPersistence.save(persistenceFile, blob);
    }

    // Iterative Deepening DFS: perform depth-limited DFS using explicit stack
    private void iddfsDepthLimited(Path root, int depthLimit)
    {
        // Depth-limited DFS implemented with stack of (Path, depth)
        Deque<Pair<Path, Integer>> stack = new ArrayDeque<>();
        if (!Files.exists(root)) return;
        stack.push(new Pair<>(root, 0));
        while (!stack.isEmpty())
        {
            Pair<Path, Integer> p = stack.pop();
            Path cur = p.first;
            int depth = p.second;
            if (shouldIgnore(cur)) continue;
            indexPathSilently(cur);
            if (depth >= depthLimit) continue;
            if (Files.isDirectory(cur)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(cur))
                {
                    for (Path child : ds) {stack.push(new Pair<>(child, depth + 1));}
                } catch (IOException ignored) {}
            }
        }
    }

    /* =========================
       Developer Utility Operations (5)
       ========================= */

    @Override
    public void openTerminalHere() {
        try {
            String cmd = System.getenv("ComSpec"); // normally cmd.exe on Windows
            if (cmd == null || cmd.isBlank()) cmd = "cmd";
            new ProcessBuilder(cmd, "/K")
                    .directory(Paths.get(currentPath).toFile())
                    .start();
        } catch (IOException ignored) {}
    }

    @Override
    public String getAbsolutePath(String fileName) {
        if (fileName == null) return currentPath;
        return Paths.get(currentPath, fileName).toAbsolutePath().toString();
    }

    @Override
    public String getRelativePath(String fileName) {
        return fileName; // caller resolves relative semantics
    }

    @Override
    public void toggleCodeMode(boolean enabled) {
        this.codeModeEnabled = enabled;
    }

    @Override
    public void applyIgnoreRules(List<String> ignorePatterns) {
        ignoreRules.clear();
        if (ignorePatterns == null) return;
        for (String s : ignorePatterns) if (s != null && !s.isBlank()) ignoreRules.add(s.trim());
    }

    /* =========================
       System & Concurrency Control (4)
       ========================= */

    @Override
    public void startBackgroundIndexer() {
        if (workerRunning) return;
        workerRunning = true;
        workerThread = new Thread(this::workerLoop, "HybridADT-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void stopBackgroundIndexer() {
        workerRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try { workerThread.join(2000); } catch (InterruptedException ignored) {}
            workerThread = null;
        }
    }

    @Override
    public void handleFileSystemEvent(FileEvent event) {
        if (event == null) return;
        // enqueue for processing
        eventQueue.offer(event);
    }

    @Override
    public void shutdown() {
        stopBackgroundIndexer();
        persistIndexToDisk();
    }

    /* =========================
       Worker & helpers
       ========================= */

    private void workerLoop() {
        while (workerRunning) {
            try {
                FileEvent ev = eventQueue.poll(1, TimeUnit.SECONDS);
                if (ev != null) processEvent(ev);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processEvent(FileEvent ev) {
        switch (ev.type()) {
            case CREATE -> updateIndexOnFileCreate(ev.path().toString());
            case MODIFY -> updateIndexOnFileModify(ev.path().toString());
            case DELETE -> updateIndexOnFileDelete(ev.path().toString());
        }
    }

    private boolean shouldIgnore(Path p) {
        if (p == null) return true;
        String s = p.toString();
        for (String rule : ignoreRules) {
            if (s.contains(rule)) return true;
        }
        return false;
    }

    private FileMetadata buildMetadata(Path p) {
        try {
            String name = p.getFileName().toString();
            String path = p.toAbsolutePath().toString();
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) ext = name.substring(dot).toLowerCase(Locale.ROOT);
            long size = Files.isDirectory(p) ? 0L : Files.size(p);
            long lm = Files.getLastModifiedTime(p).toMillis();
            boolean isDir = Files.isDirectory(p);
            return new FileMetadata(name, path, ext, size, lm, isDir);
        } catch (Exception e) {
            return null;
        }
    }

    private void indexPathSilently(Path p) {
        try {
            if (shouldIgnore(p)) return;
            FileMetadata meta = buildMetadata(p);
            if (meta == null) return;
            String key = p.toAbsolutePath().toString();
            metadataMap.put(key, meta);
            trie.insert(meta.name().toLowerCase(Locale.ROOT), key);
            if (!meta.isDirectory()) {
                // content indexing only for text-like files
                if (isTextFile(meta.extension())) {
                    try {
                        byte[] bytes = Files.readAllBytes(p);
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        inverted.index(content, key);
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void indexPath(Path p) {
        indexPathSilently(p);
    }

    private boolean isTextFile(String extension) {
        if (extension == null || extension.isBlank()) return false;
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        return TEXT_EXTS.contains(ext.toLowerCase(Locale.ROOT));
    }

    private List<FileMetadata> toMetadataList(Collection<String> paths) {
        List<FileMetadata> out = new ArrayList<>();
        for (String p : paths) {
            FileMetadata m = metadataMap.get(p);
            if (m != null) out.add(m);
        }
        return out;
    }

    /* --------------------
       Internal Data Types
       -------------------- */

    // small pair
    private static final class Pair<A, B> {
        final A first;
        final B second;
        Pair(A a, B b) { first = a; second = b; }
    }

    /* --------------------
       Trie Implementation
       -------------------- */

    private static final class Trie {
        private final TrieNode root = new TrieNode();

        void insert(String name, String fullPath) {
            TrieNode node = root;
            for (char c : name.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new TrieNode());
            }
            node.isEnd = true;
            if (!node.paths.contains(fullPath)) node.paths.add(fullPath);
        }

        List<String> searchByPrefix(String prefix) {
            TrieNode node = root;
            for (char c : prefix.toCharArray()) {
                node = node.children.get(c);
                if (node == null) return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            collect(node, result);
            return result;
        }

        void removePath(String fullPath) {
            removePathRecursive(root, fullPath);
        }

        private boolean removePathRecursive(TrieNode node, String path) {
            node.paths.remove(path);
            for (Iterator<Map.Entry<Character, TrieNode>> it = node.children.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Character, TrieNode> e = it.next();
                TrieNode child = e.getValue();
                boolean childEmpty = removePathRecursive(child, path);
                if (childEmpty && child.paths.isEmpty() && child.children.isEmpty()) {
                    it.remove();
                }
            }
            return node.paths.isEmpty() && !node.isEnd;
        }

        void clear() {
            root.children.clear();
            root.paths.clear();
            root.isEnd = false;
        }

        void buildFromPaths(Collection<String> allPaths) {
            for (String p : allPaths) {
                String name = Paths.get(p).getFileName().toString().toLowerCase(Locale.ROOT);
                insert(name, p);
            }
        }

        private void collect(TrieNode node, List<String> out) {
            out.addAll(node.paths);
            for (TrieNode child : node.children.values()) collect(child, out);
        }

        private static final class TrieNode {
            final Map<Character, TrieNode> children = new HashMap<>();
            final List<String> paths = new ArrayList<>();
            boolean isEnd = false;
        }
    }

    /* --------------------
       Inverted Index Implementation
       -------------------- */

    private static final class InvertedIndex {
        // token -> (path -> frequency)
        private final ConcurrentMap<String, ConcurrentMap<String, Integer>> index = new ConcurrentHashMap<>();

        void index(String content, String path) {
            if (content == null || content.isBlank()) return;
            String[] tokens = tokenize(content);
            Map<String, Integer> freqs = new HashMap<>();
            for (String t : tokens) if (!t.isBlank()) freqs.merge(t, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : freqs.entrySet()) {
                index.computeIfAbsent(e.getKey(), k -> new ConcurrentHashMap<>())
                        .merge(path, e.getValue(), Integer::sum);
            }
        }

        Set<String> search(String query) {
            String[] tokens = tokenize(query);
            Set<String> result = null;
            for (String t : tokens) {
                ConcurrentMap<String, Integer> posting = index.getOrDefault(t, new ConcurrentHashMap<>());
                Set<String> keys = posting.keySet();
                if (result == null) result = new HashSet<>(keys);
                else result.retainAll(keys);
                if (result.isEmpty()) break;
            }
            return result == null ? Collections.emptySet() : result;
        }

        void removePath(String path) {
            for (Iterator<Map.Entry<String, ConcurrentMap<String, Integer>>> it = index.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, ConcurrentMap<String, Integer>> e = it.next();
                e.getValue().remove(path);
                if (e.getValue().isEmpty()) it.remove();
            }
        }

        Map<String, Set<String>> snapshot() {
            Map<String, Set<String>> snap = new HashMap<>();
            for (Map.Entry<String, ConcurrentMap<String, Integer>> e : index.entrySet()) {
                snap.put(e.getKey(), new HashSet<>(e.getValue().keySet()));
            }
            return snap;
        }

        void loadFromMap(Map<String, Set<String>> map) {
            index.clear();
            if (map == null) return;
            for (Map.Entry<String, Set<String>> e : map.entrySet()) {
                ConcurrentMap<String, Integer> m = new ConcurrentHashMap<>();
                for (String p : e.getValue()) m.put(p, 1); // frequencies unknown, default to 1
                index.put(e.getKey(), m);
            }
        }

        void clear() {
            index.clear();
        }

        private static String[] tokenize(String s) {
            if (s == null) return new String[0];
            String cleaned = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (cleaned.isEmpty()) return new String[0];
            return cleaned.split("\\s+");
        }
    }

    /* --------------------
       Persistence helper
       -------------------- */

    private static final class IndexPersistence {
        static final class DataBlob implements Serializable {
            private static final long serialVersionUID = 1L;
            Map<String, FileMetadata> metadataMap;
            Map<String, Set<String>> invertedMap;
        }

        static void save(Path file, DataBlob blob) {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                oos.writeObject(blob);
            } catch (IOException e) {
                System.err.println("Index save failed: " + e.getMessage());
            }
        }

        static DataBlob load(Path file) {
            if (!Files.exists(file)) return null;
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
                Object o = ois.readObject();
                if (o instanceof DataBlob) return (DataBlob) o;
            } catch (Exception e) {
                System.err.println("Index load failed: " + e.getMessage());
            }
            return null;
        }
    }
}
