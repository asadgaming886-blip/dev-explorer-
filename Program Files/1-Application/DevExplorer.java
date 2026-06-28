//package application;

import hybridadt.FileEvent;
import hybridadt.FileMetadata;
import hybridadt.HybridADT;
import hybridadt.HybridADTInterface;
import indexing.IndexManager;
import navigation.NavigationController;
import searching.ContentSearch;
import searching.FileSearch;
import searching.SearchResultRanker;
import devtools.PathUtility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * DevExplorer - CLI test harness (Java single-file friendly).
 *
 * Usage:
 *   java DevExplorer.java
 *
 * This class contains instance main() and is invoked by the top-level statement
 * at the bottom of the file: new DevExplorer().main();
 */
public class DevExplorer {

    private final HybridADTInterface adt;
    private final IndexManager indexManager;
    private final NavigationController navigation;
    private final FileSearch fileSearch;
    private final ContentSearch contentSearch;
    private final SearchResultRanker ranker;
    private final PathUtility pathUtility;

    public DevExplorer() {
        this.adt = new HybridADT();
        this.indexManager = new IndexManager(adt);
        this.navigation = new NavigationController(adt);
        this.fileSearch = new FileSearch(adt);
        this.contentSearch = new ContentSearch(adt);
        this.ranker = new SearchResultRanker(adt);
        this.pathUtility = new PathUtility(adt);
    }

    private void printMenu() {
        IO.println("""
                
                --- Menu ---
                1  Build initial index (prompt)
                2  List current directory
                3  Change directory (open)
                4  Search by filename prefix
                5  Search by file content
                6  Reindex a directory
                7  Start/Stop background indexer
                8  Apply ignore rules (comma-separated)
                9  Persist index to disk
                10 Load index from disk
                11 Open terminal here
                12 Path utility demo
                13 Simulate delete from index
                14 Full ADT validation (runs all methods)
                0  Exit
                """);
    }

    /* ---------- Menu actions ---------- */

    private void quickBuildIndex() {
        String path = IO.readln("Root path (empty = user.home): ").strip();
        if (path.isEmpty()) path = System.getProperty("user.home");
        IO.println("Building index for: " + path + " (may take time) ...");
        indexManager.buildIndex(path);
        IO.println("Index build completed.");
    }

    private void listCurrentDirectory() {
        IO.println("Listing: " + navigation.getCurrentPath());
        List<FileMetadata> items = navigation.listCurrentDirectory();
        if (items == null || items.isEmpty()) { IO.println("(empty)"); return; }
        items.forEach(this::printMetadata);
    }

    private void changeDirectory() {
        String p = IO.readln("Directory to open: ").strip();
        if (!p.isEmpty()) {
            navigation.openDirectory(p);
            IO.println("Now at: " + navigation.getCurrentPath());
        }
    }

    private void searchByFilename() {
        String q = IO.readln("Filename prefix: ").strip();
        if (q.isEmpty()) return;
        List<FileMetadata> res = fileSearch.searchByNamePrefix(q);
        res = ranker.rank(res);
        printResults(res);
    }

    private void searchByContent() {
        String q = IO.readln("Content query: ").strip();
        if (q.isEmpty()) return;
        List<FileMetadata> res = contentSearch.search(q);
        res = ranker.rank(res);
        printResults(res);
    }

    private void reindexDirectory() {
        String p = IO.readln("Directory to reindex: ").strip();
        if (!p.isEmpty()) {
            indexManager.reindexDirectory(p);
            IO.println("Reindex requested for: " + p);
        }
    }

    private void toggleBackgroundIndexer() {
        String c = IO.readln("1=start, 2=stop: ").strip();
        if ("1".equals(c)) { indexManager.startBackgroundIndexing(); IO.println("Started."); }
        else if ("2".equals(c)) { indexManager.stopBackgroundIndexing(); IO.println("Stopped."); }
    }

    private void applyIgnoreRules() {
        String in = IO.readln("Ignore substrings (comma-separated): ").strip();
        if (in.isEmpty()) return;
        List<String> rules = List.of(in.split(","));
        adt.applyIgnoreRules(rules);
        IO.println("Applied ignore rules: " + rules);
    }

    private void persistIndex() {
        adt.persistIndexToDisk();
        IO.println("Persisted index to disk.");
    }

    private void loadIndex() {
        adt.loadIndexFromDisk();
        IO.println("Loaded index from disk.");
    }

    private void openTerminalHere() {
        try {
            adt.openTerminalHere();
            IO.println("Attempted to open terminal at current path.");
        } catch (Exception e) {
            IO.println("openTerminalHere failed: " + e);
        }
    }

    private void showPathUtility() {
        String f = IO.readln("Filename for absolute path (leave empty to skip): ").strip();
        if (!f.isEmpty()) IO.println("Absolute: " + pathUtility.absolutePathOf(f));
        IO.println("Current path: " + pathUtility.currentDirectory());
    }

    private void simulateDelete() {
        String p = IO.readln("Absolute path to remove from index: ").strip();
        if (p.isEmpty()) return;
        indexManager.handleFileEvent(new FileEvent(FileEvent.EventType.DELETE, Paths.get(p)));
        IO.println("Requested delete event for: " + p);
    }

    /* ---------- Full ADT validation ---------- */

    private void runFullADTValidation() {
        IO.println("Running Full ADT Validation (temporary directory)...");
        try {
            Path tmp = Files.createTempDirectory("devexplorer_test_");
            Path f1 = tmp.resolve("sample1.txt");
            Path f2 = tmp.resolve("sample2.java");
            Path b1 = tmp.resolve("binary.bin");

            Files.writeString(f1, "hello world\nthis is a sample file");
            Files.writeString(f2, "public class Sample {}");
            Files.write(b1, new byte[]{0,1,2,3,4,5});

            // 1) buildInitialIndex
            IO.println("1) buildInitialIndex on: " + tmp);
            adt.buildInitialIndex(tmp.toString());

            // 2) navigation & listing
            IO.println("2) openDirectory / listCurrentDirectory");
            adt.openDirectory(tmp.toString());
            IO.println("currentPath -> " + adt.getCurrentPath());
            List<FileMetadata> list = adt.listCurrentDirectory();
            IO.println("listCurrentDirectory -> " + (list == null ? 0 : list.size()));

            // 3) filename search
            IO.println("3) searchByFilenamePrefix('sample') ->");
            printResults(adt.searchByFilenamePrefix("sample"));

            // 4) content search
            IO.println("4) searchByFileContent('hello') ->");
            printResults(adt.searchByFileContent("hello"));

            // 5) search in current directory
            IO.println("5) searchInCurrentDirectory('public') ->");
            printResults(adt.searchInCurrentDirectory("public"));

            // 6) filter by extension
            IO.println("6) filterByExtension('.java') ->");
            printResults(adt.filterByExtension(".java"));

            // 7) ranking
            IO.println("7) rankSearchResults(sample name results) ->");
            printResults(adt.rankSearchResults(adt.searchByFilenamePrefix("sample")));

            // 8) clearSearch
            IO.println("8) clearSearch()");
            adt.clearSearch();

            // 9) create/modify/delete updates
            IO.println("9) updateIndexOnFileCreate (newfile)");
            Path nf = tmp.resolve("newfile.txt");
            Files.writeString(nf, "dynamic create");
            adt.updateIndexOnFileCreate(nf.toString());
            printResults(adt.searchByFilenamePrefix("newfile"));

            IO.println("10) updateIndexOnFileModify (modify sample1)");
            Files.writeString(f1, "hello modified content");
            adt.updateIndexOnFileModify(f1.toString());
            printResults(adt.searchByFileContent("modified"));

            IO.println("11) updateIndexOnFileDelete (delete newfile)");
            Files.deleteIfExists(nf);
            adt.updateIndexOnFileDelete(nf.toString());
            IO.println("Exists after delete? " + Files.exists(nf));

            // 12) reindexDirectory
            IO.println("12) reindexDirectory");
            adt.reindexDirectory(tmp.toString());

            // 13) persist & load
            IO.println("13) persistIndexToDisk()");
            adt.persistIndexToDisk();
            IO.println(" -> loadIndexFromDisk()");
            adt.loadIndexFromDisk();

            // 14) openTerminalHere (best effort)
            IO.println("14) openTerminalHere()");
            try { adt.openTerminalHere(); } catch (Exception e) { IO.println("openTerminalHere error: " + e); }

            // 15) getAbsolutePath / getRelativePath
            IO.println("15) getAbsolutePath & getRelativePath");
            IO.println(adt.getAbsolutePath("sample1.txt"));
            IO.println(adt.getRelativePath("sample1.txt"));

            // 16) toggle code mode & ignore rules
            IO.println("16) toggleCodeMode(true)");
            adt.toggleCodeMode(true);
            IO.println("16b) applyIgnoreRules(.git,node_modules)");
            adt.applyIgnoreRules(List.of(".git", "node_modules"));

            // 17) start/stop background indexer & handle event
            IO.println("17) startBackgroundIndexer()");
            adt.startBackgroundIndexer();
            FileEvent fe = new FileEvent(FileEvent.EventType.CREATE, f1);
            IO.println(" -> handleFileSystemEvent(CREATE sample1)");
            adt.handleFileSystemEvent(fe);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            IO.println(" -> stopBackgroundIndexer()");
            adt.stopBackgroundIndexer();

            // 18) final checks & shutdown
            IO.println("18) final sanity search (sample1)");
            printResults(adt.searchByFilenamePrefix("sample1"));

            IO.println("19) shutdown()");
            adt.shutdown();

            // cleanup temp files
            try { Files.deleteIfExists(f1); Files.deleteIfExists(f2); Files.deleteIfExists(b1); Files.deleteIfExists(tmp); } catch (Exception ignored) {}

            IO.println("Full ADT validation completed.");
        } catch (Exception e) {
            IO.println("Full ADT validation failed: " + e);
        }
    }

    /* Helpers */
    private void printResults(List<FileMetadata> res) {
        if (res == null || res.isEmpty()) { IO.println("(no results)"); return; }
        for (FileMetadata m : res) printMetadata(m);
    }

    private void printMetadata(FileMetadata m) {
        IO.println((m.isDirectory() ? "<DIR>" : (m.size() + " bytes")) + "  " + m.extension() + "  " + m.path());
    }

    /* Instance main - interactive loop using IO.readln / IO.println */
    void entry_point() {
        IO.println("=== DevExplorer CLI (Full ADT Validation) ===");
        IO.println("Attempting to load persisted index (if present)...");
        try { adt.loadIndexFromDisk(); } catch (Exception e) { IO.println("loadIndexFromDisk failed: " + e); }

        boolean running = true;
        while (running) {
            printMenu();
            String choice = IO.readln("choice> ").strip();
            switch (choice) {
                case "1" -> quickBuildIndex();
                case "2" -> listCurrentDirectory();
                case "3" -> changeDirectory();
                case "4" -> searchByFilename();
                case "5" -> searchByContent();
                case "6" -> reindexDirectory();
                case "7" -> toggleBackgroundIndexer();
                case "8" -> applyIgnoreRules();
                case "9" -> persistIndex();
                case "10" -> loadIndex();
                case "11" -> openTerminalHere();
                case "12" -> showPathUtility();
                case "13" -> simulateDelete();
                case "14" -> runFullADTValidation();
                case "0" ->
                {
                    IO.println("Shutting down and persisting index...");
                    adt.shutdown();
                    running = false;
                }
                default -> IO.println("Unknown choice.");
            }
        }
        IO.println("DevExplorer exited.");
    }
}

void main() {new DevExplorer().entry_point();}