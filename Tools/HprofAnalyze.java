/*
 * HprofAnalyze - standalone Android (ART) heap dump analyzer.
 *
 * Answers the question a class histogram cannot: *what is keeping this object alive*.
 * Parses hprof 1.0.3 with 4-byte ids, builds the full object graph, computes the
 * dominator tree (Lengauer-Tarjan) for retained sizes, and lets you interrogate
 * reference chains and GC roots. A 650 MB dump takes ~6s and ~1.5 GB of heap.
 *
 * Usage:
 *     java -Xmx4g Tools/HprofAnalyze.java <dump.hprof> [command...]
 *
 * With no command it prints the overview report: per-heap totals, top classes by
 * shallow / retained size / instance count, top objects by retained size.
 *
 * Commands (several may be chained in one run - the dump is parsed once):
 *
 *   --count      <class>                 instances, shallow and retained totals
 *   --band       <lo> <hi>               classes whose instance count is in [lo,hi];
 *                                        finds "one leaked object per screen" patterns
 *   --classinfo  <substr>                class -> superclass chain
 *   --who        <class>                 inbound references, aggregated by holder.field, with the
 *                                        shallow size they hold
 *   --dom        <class>                 immediate dominators, aggregated, with the size retained
 *   --holders    <0xid>                  every inbound reference of one object
 *   --ancestors  <0xid> <levels>         reference tree walked backwards
 *   --retains    <0xid> <levels>         dominator tree walked forwards: what it holds
 *   --paths      <class> <n>             shortest GC-root path for the n biggest
 *   --fields     <0xid>                  all field values of one object
 *   --fieldstats <class> <f1,f2,...>     value distribution of fields across instances;
 *                                        the fastest way to tell live objects from dead ones.
 *                                        Fields may be paths (mCallback.referent,specs.indicatorSize)
 *                                        with array elements (imageReceiverArray.elementData[0].thumbTag);
 *                                        a+b groups by both at once (mFile.path+mMap.size)
 *   --strings    <class>                 decoded String fields of every instance
 *   --roottype   <class>                 which GC root kind roots each instance
 *   --rootstats                          all GC roots by kind and class
 *   --jni                                distinct objects held by JNI global refs
 *   --jniref     <class>                 field shapes of JNI-global instances
 *   --freeif     <classes>               bytes/objects freed if those were released
 *   --survivors  <drop> <check>          how <check> instances stay reachable once <drop> is
 *                                        gone, one entry per distinct path
 *   --remaining  <classes>               class histogram of what stays reachable without them
 *   --histo      <n>                     reachable-only class histogram as count<TAB>bytes<TAB>class,
 *                                        top n by size (0 = all); diff two dumps' output to see growth
 *   --bitmaps    <classes|->             Bitmap pixel memory (width*height*4) those would free vs
 *                                        keep; the pixels are native, the Java heap never shows them
 *   --elems      <0xid>                  elements of an Object[], ArrayList, SparseArray or ArrayMap
 *   --keys       <class|0xid>            keys of a HashMap or SharedPreferences, grouped by pattern
 *                                        (digit runs -> #), with an example key and value
 *   --ncobs      <min>                   NotificationCenter observers per event id and account, for
 *                                        lists with at least <min> entries; id names come from the
 *                                        class statics, weak group wrappers are resolved, and
 *                                        observers nothing else keeps alive are marked (only here)
 *   --animcb                             animators ticking on each AnimationHandler, with target,
 *                                        listeners and how long they have been running
 *   --queue                              pending Handler messages, per Looper queue
 *   --hooks                              installed Xposed method hooks, by plugin
 *
 * <class> matches as a substring; prefix with '=' for an exact match
 * (=org.telegram.ui.ChatActivity excludes ChatActivity$18 and friends), or with
 * '^' to match that class and every subclass of it (^ChatActivityEnterView finds
 * the anonymous ChatActivity$40 that actually holds the instances).
 * Append @path=value (exact), @path~text (substring) or @path>n / @path<n (numeric) to keep only
 * matching instances: =org.telegram.ui.ChatActivity@isFinished=true,
 * =android.animation.ObjectAnimator@mTarget~Loading, =android.app.SharedPreferencesImpl@mMap.size>2000.
 * <classes> is a comma-separated list of <class>.
 * <0xid> is an object id as printed by the other commands.
 *
 * Note: `referent` edges on java.lang.ref.Reference subclasses are deliberately NOT
 * followed. Treating weak/soft/phantom refs as strong invents retention paths that
 * the GC would never honour and badly under-reports real leaks.
 */
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class HprofAnalyze {

    static MappedByteBuffer b;
    static int idSize = 4;
    static int headerEnd;

    // ---------------- int -> int open addressing ----------------
    static final class IntIntMap {
        int[] keys; int[] vals; int mask; int size;
        IntIntMap(int cap) {
            int c = Integer.highestOneBit(Math.max(16, cap)) * 4;
            keys = new int[c]; vals = new int[c]; mask = c - 1;
            Arrays.fill(vals, -1);
        }
        static int hash(int k) { k *= 0x9E3779B1; return (k ^ (k >>> 16)); }
        void put(int k, int v) {
            int i = hash(k) & mask;
            while (vals[i] != -1) { if (keys[i] == k) { vals[i] = v; return; } i = (i + 1) & mask; }
            keys[i] = k; vals[i] = v; size++;
            if ((long) size * 2 > keys.length) grow();
        }
        int get(int k) {
            int i = hash(k) & mask;
            while (vals[i] != -1) { if (keys[i] == k) return vals[i]; i = (i + 1) & mask; }
            return -1;
        }
        void grow() {
            int[] ok = keys, ov = vals;
            keys = new int[ok.length * 2]; vals = new int[ok.length * 2];
            mask = keys.length - 1; Arrays.fill(vals, -1); size = 0;
            for (int i = 0; i < ok.length; i++) if (ov[i] != -1) put(ok[i], ov[i]);
        }
    }

    static final class IntList {
        int[] a = new int[1024]; int n;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
        int get(int i) { return a[i]; }
    }

    // ---------------- class metadata ----------------
    static final class ClassMeta {
        int id, superId, instanceSize;
        String name;
        int[] fNameId; byte[] fType;                 // declared instance fields
        int[] staticObjVal; String[] staticObjName;  // static object-typed fields
        int[] objFieldOff;  String[] objFieldName;   // resolved incl. supers
        int staticsPos;                              // file offset of the static field block
        int nodeIdx = -1;
    }

    static HashMap<Integer, String> strings = new HashMap<>(1 << 18);
    static HashMap<Integer, Integer> classNameId = new HashMap<>(1 << 15);   // classObjId -> nameStringId
    static HashMap<Integer, ClassMeta> classes = new HashMap<>(1 << 15);

    static int typeSize(int t) {
        switch (t) {
            case 2: return idSize;
            case 4: case 8: return 1;
            case 5: case 9: return 2;
            case 6: case 10: return 4;
            case 7: case 11: return 8;
        }
        throw new RuntimeException("bad type " + t);
    }

    // ---------------- node arrays ----------------
    static int N;
    static IntIntMap id2idx;
    static int[] nClass;    // classObjId of the node (for class nodes: the class it represents = own id)
    static int[] nOff;      // file offset of the field-data / element-data
    static byte[] nKind;    // 0 instance, 1 obj array, 2 prim array, 3 class object
    static int[] nSize;     // shallow size
    static int[] nCount;    // array element count
    static byte[] nHeap;    // heap id
    static int[] nIdList;   // node index -> object id

    static IntList rootIds = new IntList();
    static IntList rootKinds = new IntList();

    static String[] heapNames = new String[256];

    static String cname(int classObjId) {
        ClassMeta m = classes.get(classObjId);
        if (m != null && m.name != null) return m.name;
        Integer sid = classNameId.get(classObjId);
        if (sid != null) { String s = strings.get(sid); if (s != null) return s; }
        return "0x" + Integer.toHexString(classObjId);
    }

    static String nodeName(int i) {
        if (nKind[i] == 3) return "class " + cname(nClass[i]);
        if (nKind[i] == 1) return cname(nClass[i]) + "[" + nCount[i] + "]";
        if (nKind[i] == 2) return primName(nClass[i]) + "[" + nCount[i] + "]";
        return cname(nClass[i]);
    }

    static String primName(int t) {
        switch (t) { case 4: return "boolean"; case 5: return "char"; case 6: return "float";
            case 7: return "double"; case 8: return "byte"; case 9: return "short";
            case 10: return "int"; case 11: return "long"; }
        return "prim";
    }

    // ---------------- parsing ----------------
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            usage();
            return;
        }
        String path = args[0];
        RandomAccessFile raf = new RandomAccessFile(path, "r");
        FileChannel ch = raf.getChannel();
        long flen = ch.size();
        if (flen > Integer.MAX_VALUE)
            throw new RuntimeException("dump is " + flen + " bytes; file offsets are held in int, so 2 GB is the ceiling");
        b = ch.map(FileChannel.MapMode.READ_ONLY, 0, flen);
        b.order(ByteOrder.BIG_ENDIAN);

        StringBuilder sb = new StringBuilder();
        byte c;
        while ((c = b.get()) != 0) sb.append((char) c);
        idSize = b.getInt();
        b.getLong();
        headerEnd = b.position();
        System.err.println("format=" + sb + " idSize=" + idSize + " fileLen=" + flen);
        if (idSize != 4) throw new RuntimeException("idSize " + idSize + " unsupported");

        long t0 = System.currentTimeMillis();
        int[] counts = pass1();
        System.err.println("pass1 " + (System.currentTimeMillis() - t0) + "ms  instances=" + counts[0]
                + " objArrays=" + counts[1] + " primArrays=" + counts[2] + " classes=" + classes.size()
                + " strings=" + strings.size() + " roots=" + rootIds.n);

        N = counts[0] + counts[1] + counts[2] + classes.size();
        id2idx = new IntIntMap(N);
        nClass = new int[N]; nOff = new int[N]; nKind = new byte[N];
        nSize = new int[N]; nCount = new int[N]; nHeap = new byte[N]; nIdList = new int[N];

        t0 = System.currentTimeMillis();
        pass2();
        System.err.println("pass2 " + (System.currentTimeMillis() - t0) + "ms  nodes=" + N);

        t0 = System.currentTimeMillis();
        resolveFieldLayouts();
        buildEdges();
        System.err.println("edges " + (System.currentTimeMillis() - t0) + "ms  E=" + eTgt.length);

        t0 = System.currentTimeMillis();
        computeDominators();
        System.err.println("dominators " + (System.currentTimeMillis() - t0) + "ms reachable=" + reachCount);

        if (args.length > 1) query(args); else report(args);
    }

    /** Union of comma-separated class specs, deduplicated, biggest retained first. */
    static List<Integer> findByClasses(String specs) {
        LinkedHashSet<Integer> r = new LinkedHashSet<>();
        for (String spec : specs.split(",")) r.addAll(findByClass(spec));
        ArrayList<Integer> l = new ArrayList<>(r);
        l.sort((x, y) -> Long.compare(retained[y], retained[x]));
        return l;
    }

    static List<Integer> findByClass(String sub) {
        String filter = null;
        int at = sub.indexOf('@');
        if (at > 0) { filter = sub.substring(at + 1); sub = sub.substring(0, at); }
        ArrayList<Integer> r = new ArrayList<>();
        HashSet<Integer> subclasses = sub.startsWith("^") ? classAndSubclasses(sub.substring(1)) : null;
        for (int i = 0; i < N; i++) {
            if (nKind[i] == 3) continue;
            boolean hit;
            if (subclasses != null) {
                hit = subclasses.contains(nClass[i]);
            } else {
                String nm = nKind[i] == 2 ? primName(nClass[i]) + "[]" : cname(nClass[i]);
                hit = sub.startsWith("=") ? nm.equals(sub.substring(1)) : nm.contains(sub);
            }
            if (hit) r.add(i);
        }
        if (filter != null) {
            int cut = -1;
            for (int k = 0; k < filter.length() && cut < 0; k++) if ("=~<>".indexOf(filter.charAt(k)) >= 0) cut = k;
            if (cut < 0) throw new IllegalArgumentException("filter needs one of = ~ < >: @" + filter);
            char op = filter.charAt(cut);
            String path = filter.substring(0, cut), want = filter.substring(cut + 1);
            r.removeIf(o -> {
                String v = fieldValue(o, path);
                if (v.length() > 1 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
                if (op == '=') return !v.equals(want);
                if (op == '~') return !v.contains(want);
                try {
                    double d = Double.parseDouble(v), w = Double.parseDouble(want);
                    return op == '>' ? !(d > w) : !(d < w);
                } catch (NumberFormatException e) {
                    return true;
                }
            });
        }
        r.sort((x, y) -> Long.compare(retained[y], retained[x]));
        return r;
    }

    /** Class ids whose superclass chain contains an exactly-named class. */
    static HashSet<Integer> classAndSubclasses(String name) {
        HashSet<Integer> hit = new HashSet<>();
        for (ClassMeta m : classes.values()) {
            ClassMeta c = m;
            int g = 0;
            while (c != null && g++ < 40) {
                if (name.equals(c.name)) { hit.add(m.id); break; }
                c = c.superId == 0 ? null : classes.get(c.superId);
            }
        }
        return hit;
    }

    static void query(String[] args) {
        for (int ai = 1; ai < args.length; ai++) {
            String cmd = args[ai];
            if (cmd.equals("--who")) {
                String sub = args[++ai];
                List<Integer> objs = findByClass(sub);
                System.out.println("\n=== INBOUND HOLDERS OF " + sub + " (" + objs.size() + " instances; count, shallow size held) ===");
                TreeMap<String, long[]> agg = new TreeMap<>();
                for (int o : objs) {
                    if (dfn[o] == 0) { long[] a = agg.computeIfAbsent("<<UNREACHABLE>>", z -> new long[2]); a[0]++; a[1] += nSize[o]; continue; }
                    HashSet<String> seen = new HashSet<>();
                    for (int e = predHead[o]; e < predHead[o + 1]; e++) {
                        int p = predTgt[e];
                        String k = (p == SUPER ? "GC-ROOT" : nodeName(p)) + edgeLabel(p, o);
                        if (seen.add(k)) { long[] a = agg.computeIfAbsent(k, z -> new long[2]); a[0]++; a[1] += nSize[o]; }
                    }
                }
                agg.entrySet().stream().sorted((x, y) -> x.getValue()[1] != y.getValue()[1] ? Long.compare(y.getValue()[1], x.getValue()[1]) : Long.compare(y.getValue()[0], x.getValue()[0])).limit(60)
                        .forEach(e -> System.out.printf("  %6d  %10s  %s%n", e.getValue()[0], hs(e.getValue()[1]), e.getKey()));
            } else if (cmd.equals("--dom")) {
                String sub = args[++ai];
                List<Integer> objs = findByClass(sub);
                System.out.println("\n=== DOMINATORS OF " + sub + " (" + objs.size() + "; count, retained through them) ===");
                TreeMap<String, long[]> agg = new TreeMap<>();
                for (int o : objs) {
                    if (dfn[o] == 0) continue;
                    int d = idom[o];
                    long[] a = agg.computeIfAbsent(d == SUPER ? "GC-ROOT" : nodeName(d), z -> new long[2]);
                    a[0]++; a[1] += retained[o];
                }
                agg.entrySet().stream().sorted((x, y) -> x.getValue()[1] != y.getValue()[1] ? Long.compare(y.getValue()[1], x.getValue()[1]) : Long.compare(y.getValue()[0], x.getValue()[0])).limit(40)
                        .forEach(e -> System.out.printf("  %6d  %10s  %s%n", e.getValue()[0], hs(e.getValue()[1]), e.getKey()));
            } else if (cmd.equals("--paths")) {
                String sub = args[++ai];
                int lim = Integer.parseInt(args[++ai]);
                List<Integer> objs = findByClass(sub);
                System.out.println("\n=== PATHS TO GC ROOT: " + sub + " (" + objs.size() + " instances, showing " + Math.min(lim, objs.size()) + ") ===");
                for (int k = 0; k < Math.min(lim, objs.size()); k++) {
                    int o = objs.get(k);
                    System.out.printf("%n--- #%d %s @0x%x retained=%s%n", k, nodeName(o), nIdList[o], hs(retained[o]));
                    printPath(o);
                }
            } else if (cmd.equals("--fields")) {
                int oid = (int) Long.parseLong(args[++ai].replace("0x", ""), 16);
                dumpFields(id2idx.get(oid));
            } else if (cmd.equals("--count")) {
                String sub = args[++ai];
                List<Integer> objs = findByClass(sub);
                long sh = 0, rt = 0;
                for (int o : objs) { sh += nSize[o]; rt += retained[o]; }
                System.out.printf("%s : %d instances, shallow=%s retained=%s%n", sub, objs.size(), hs(sh), hs(rt));
            } else if (cmd.equals("--holders")) {
                int oid = (int) Long.parseLong(args[++ai].replace("0x", ""), 16);
                int o = id2idx.get(oid);
                System.out.printf("%n=== INBOUND of %s @0x%x ===%n", nodeName(o), oid);
                for (int e = predHead[o]; e < predHead[o + 1]; e++) {
                    int pp = predTgt[e];
                    System.out.printf("   %s%s  @0x%x  ret=%s%n", pp == SUPER ? "GC-ROOT" : nodeName(pp),
                            edgeLabel(pp, o), pp == SUPER ? 0 : nIdList[pp], pp == SUPER ? "-" : hs(retained[pp]));
                }
            } else if (cmd.equals("--ancestors")) {
                int oid = (int) Long.parseLong(args[++ai].replace("0x", ""), 16);
                int lv = Integer.parseInt(args[++ai]);
                System.out.printf("%n=== ANCESTORS of @0x%x ===%n", oid);
                ancestors(id2idx.get(oid), lv, 0, new HashSet<>());
            } else if (cmd.equals("--retains")) {
                int oid = (int) Long.parseLong(args[++ai].replace("0x", ""), 16);
                int lv = Integer.parseInt(args[++ai]);
                int o = id2idx.get(oid);
                if (o < 0) { System.out.println("not found"); continue; }
                if (dfn[o] == 0) { System.out.println("unreachable, retains nothing"); continue; }
                buildDomChildren();
                retainsFloor = Math.max(retained[o] / 200, 64);
                System.out.printf("%n=== RETAINED BY %s @0x%x : %s (folding children under %s) ===%n",
                        nodeName(o), oid, hs(retained[o]), hs(retainsFloor));
                retainsTree(o, lv, 0);
            } else if (cmd.equals("--roottype")) {
                String sub = args[++ai];
                List<Integer> objs = findByClass(sub);
                HashMap<Integer, Integer> kindOf = new HashMap<>();
                HashMap<Integer, Integer> kindCount = new HashMap<>();
                for (int r = 0; r < rootIds.n; r++) {
                    int idx = id2idx.get(rootIds.get(r));
                    if (idx >= 0) kindOf.merge(idx, 1 << rootKinds.get(r), (a2, b2) -> a2 | b2);
                }
                TreeMap<String, Integer> agg = new TreeMap<>();
                for (int o : objs) {
                    Integer m2 = kindOf.get(o);
                    if (m2 == null) { agg.merge("(not a root)", 1, Integer::sum); continue; }
                    StringBuilder sb2 = new StringBuilder();
                    for (int k = 0; k < 32; k++) if ((m2 & (1 << k)) != 0) sb2.append(rootName(k)).append(" ");
                    agg.merge(sb2.toString(), 1, Integer::sum);
                }
                System.out.println("\n=== ROOT TYPES for " + sub + " (" + objs.size() + ") ===");
                agg.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue())
                        .forEach(e -> System.out.printf("  %6d  %s%n", e.getValue(), e.getKey()));
            } else if (cmd.equals("--jniref")) {
                String want = args[++ai];
                TreeMap<String,Integer> mm = new TreeMap<>();
                HashSet<Integer> uniq = new HashSet<>();
                for (int r = 0; r < rootIds.n; r++) {
                    if (rootKinds.get(r) != 0x01) continue;
                    int idx = id2idx.get(rootIds.get(r));
                    if (idx < 0 || !uniq.add(idx)) continue;
                    if (!nodeName(idx).contains(want)) continue;
                    ClassMeta m2 = classes.get(nClass[idx]);
                    StringBuilder sb2 = new StringBuilder();
                    if (m2 != null) for (int f = 0; f < m2.objFieldOff.length; f++) {
                        if (m2.objFieldOff[f] + idSize > nCount[idx]) continue;
                        int v = b.getInt(nOff[idx] + m2.objFieldOff[f]);
                        int ti = v == 0 ? -1 : id2idx.get(v);
                        sb2.append(m2.objFieldName[f]).append("=").append(ti < 0 ? (v==0?"null":"?") : nodeName(ti)).append("  ");
                    }
                    mm.merge(sb2.toString(), 1, Integer::sum);
                }
                System.out.println("\n=== JNI-GLOBAL " + want + " field shapes ===");
                mm.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(25)
                        .forEach(e -> System.out.printf("  %6d  %s%n", e.getValue(), e.getKey()));
            } else if (cmd.equals("--fieldstats")) {
                String sub = args[++ai];
                String[] want = args[++ai].split(",");
                List<Integer> objs = findByClass(sub);
                System.out.println("\n=== FIELD STATS " + sub + " (" + objs.size() + " instances) ===");
                for (String w : want) {
                    TreeMap<String,Integer> mm = new TreeMap<>();
                    for (int o : objs) {
                        StringJoiner key = new StringJoiner("  ");
                        for (String part : w.split("\\+")) key.add(fieldValue(o, part));
                        mm.merge(key.toString(), 1, Integer::sum);
                    }
                    System.out.println("  " + w + ":");
                    mm.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(8)
                            .forEach(e -> System.out.printf("      %6d  %s%n", e.getValue(), e.getKey()));
                }
            } else if (cmd.equals("--strings")) {
                String sub = args[++ai];
                List<Integer> objs = findByClass(sub);
                System.out.println("\n=== STRING FIELDS of " + sub + " (" + objs.size() + ") ===");
                int printed = 0;
                for (int o : objs) {
                    if (printed++ == 500) { System.out.printf("  ... %,d more not shown%n", objs.size() - 500); break; }
                    StringBuilder sb2 = new StringBuilder();
                    ClassMeta m2 = classes.get(nClass[o]);
                    if (m2 == null) continue;
                    for (int f = 0; f < m2.objFieldOff.length; f++) {
                        if (m2.objFieldOff[f] + idSize > nCount[o]) continue;
                        int v = b.getInt(nOff[o] + m2.objFieldOff[f]);
                        if (v == 0) continue;
                        int ti = id2idx.get(v);
                        if (ti < 0) continue;
                        String dec = decodeString(ti);
                        if (dec != null) sb2.append(m2.objFieldName[f]).append("=\"").append(dec).append("\"  ");
                    }
                    System.out.printf("  @0x%x  %s%n", nIdList[o], sb2);
                }
            } else if (cmd.equals("--freeif")) {
                String sub = args[++ai];
                List<Integer> objs = findByClasses(sub);
                int[] par = reachWithout(objs);
                long freed = 0; int cntFreed = 0;
                TreeMap<String, long[]> byClass = new TreeMap<>();
                for (int i = 0; i < N; i++) {
                    if (dfn[i] != 0 && par[i] == -2) {
                        freed += nSize[i]; cntFreed++;
                        long[] arr = byClass.computeIfAbsent(nodeName(i).replaceAll("\\[[0-9]+\\]", "[]"), z -> new long[2]);
                        arr[0]++; arr[1] += nSize[i];
                    }
                }
                System.out.printf("%n=== IF %d %s WERE RELEASED ===%n  freed: %s across %,d objects%n",
                        objs.size(), sub, hs(freed), cntFreed);
                byClass.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1])).limit(30)
                        .forEach(e -> System.out.printf("   %10s  %,8d  %s%n", hs(e.getValue()[1]), e.getValue()[0], e.getKey()));
            } else if (cmd.equals("--survivors")) {
                String dropCls = args[++ai];
                String checkCls = args[++ai];
                int[] par2 = reachWithout(findByClasses(dropCls));
                List<Integer> chk = findByClasses(checkCls);
                int alive = 0;
                LinkedHashMap<String, Integer> shapes = new LinkedHashMap<>();
                for (int o : chk) {
                    if (par2[o] == -2) continue;
                    alive++;
                    ArrayList<Integer> chain = new ArrayList<>();
                    int c = o; int g = 0;
                    while (c != -1 && c != -2 && g++ < 60) { chain.add(c); if (c == SUPER) break; c = par2[c]; }
                    Collections.reverse(chain);
                    StringBuilder sb2 = new StringBuilder();
                    for (int i = 0; i < chain.size(); i++) {
                        int u = chain.get(i);
                        String lbl = (i + 1 < chain.size()) ? edgeLabel(u, chain.get(i + 1)) : "";
                        sb2.append(String.format("    %s%s%s%n", "  ".repeat(Math.min(i, 20)), u == SUPER ? "GC ROOT" : nodeName(u), lbl));
                    }
                    shapes.merge(sb2.toString().replaceAll("\\[[0-9]+\\]", "[]"), 1, Integer::sum);
                }
                System.out.printf("%n=== AFTER DROPPING %s: %d/%d %s STILL REACHABLE, %d distinct paths ===%n",
                        dropCls, alive, chk.size(), checkCls, shapes.size());
                shapes.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(10)
                        .forEach(e -> System.out.print("  --- " + e.getValue() + " via:\n" + e.getKey()));
            } else if (cmd.equals("--classinfo")) {
                String sub = args[++ai];
                for (ClassMeta m2 : classes.values()) {
                    if (m2.name == null || !m2.name.contains(sub)) continue;
                    StringBuilder sb2 = new StringBuilder(m2.name);
                    ClassMeta c2 = m2.superId == 0 ? null : classes.get(m2.superId);
                    int g = 0;
                    while (c2 != null && g++ < 12) { sb2.append("  ->  ").append(c2.name); c2 = c2.superId == 0 ? null : classes.get(c2.superId); }
                    System.out.println("  " + sb2);
                }
            } else if (cmd.equals("--hooks")) {
                List<Integer> objs = findByClass("=de.robv.android.xposed.XC_MethodHook$Unhook");
                System.out.println("\n=== INSTALLED METHOD HOOKS (" + objs.size() + " Unhook objects) ===");
                TreeMap<String,Integer> agg = new TreeMap<>();
                for (int o : objs) {
                    int hookMethod = fieldNode(o, "hookMethod");
                    int owner = fieldNode(o, "this$0");
                    String plugin = "?";
                    if (owner >= 0) {
                        int pid = fieldNode(owner, "pluginId");
                        if (pid >= 0) plugin = decodeString(pid);
                    }
                    String target = "?";
                    if (hookMethod >= 0) {
                        int dc = fieldNode(hookMethod, "declaringClass");
                        if (dc >= 0) target = nodeName(dc).replace("class ", "");
                        target = (nKind[hookMethod] == 0 ? cname(nClass[hookMethod]).replace("java.lang.reflect.", "") : "?") + " on " + target;
                    }
                    agg.merge(String.format("%-24s  %s", plugin, target), 1, Integer::sum);
                }
                agg.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue())
                        .forEach(e -> System.out.printf("  %3d  %s%n", e.getValue(), e.getKey()));
            } else if (cmd.equals("--band")) {
                int lo = Integer.parseInt(args[++ai]), hi = Integer.parseInt(args[++ai]);
                TreeMap<String,long[]> hh = new TreeMap<>();
                for (int i = 0; i < N; i++) {
                    if (nKind[i] != 0) continue;
                    long[] arr = hh.computeIfAbsent(cname(nClass[i]), z -> new long[2]);
                    arr[0]++; arr[1] += retained[i];
                }
                System.out.printf("%n=== CLASSES WITH %d..%d INSTANCES ===%n", lo, hi);
                hh.entrySet().stream().filter(e -> e.getValue()[0] >= lo && e.getValue()[0] <= hi)
                        .sorted((x,y)->Long.compare(y.getValue()[1], x.getValue()[1])).limit(70)
                        .forEach(e -> System.out.printf("  %5d  ret=%10s  %s%n", e.getValue()[0], hs(e.getValue()[1]), e.getKey()));
            } else if (cmd.equals("--jni")) {
                TreeMap<String,Integer> mm = new TreeMap<>();
                HashSet<Integer> uniq = new HashSet<>();
                for (int r = 0; r < rootIds.n; r++) {
                    if (rootKinds.get(r) != 0x01) continue;
                    int idx = id2idx.get(rootIds.get(r));
                    if (idx >= 0 && uniq.add(idx)) mm.merge(nodeName(idx), 1, Integer::sum);
                }
                System.out.println("\n=== DISTINCT OBJECTS HELD BY JNI GLOBAL REFS (" + uniq.size() + ") ===");
                mm.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue()).limit(120)
                        .forEach(e -> System.out.printf("  %6d  %s%n", e.getValue(), e.getKey()));
            } else if (cmd.equals("--rootstats")) {
                int[] cnt = new int[256];
                HashMap<Integer, TreeMap<String,Integer>> byKind = new HashMap<>();
                for (int r = 0; r < rootIds.n; r++) {
                    int k = rootKinds.get(r); cnt[k]++;
                    int idx = id2idx.get(rootIds.get(r));
                    if (idx >= 0) byKind.computeIfAbsent(k, z -> new TreeMap<>()).merge(nodeName(idx), 1, Integer::sum);
                }
                System.out.println("\n=== GC ROOT STATISTICS ===");
                for (int k = 0; k < 256; k++) if (cnt[k] > 0) {
                    System.out.printf("%n  %s : %d roots%n", rootName(k), cnt[k]);
                    TreeMap<String,Integer> mm = byKind.get(k);
                    if (mm != null) mm.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(25)
                            .forEach(e -> System.out.printf("      %7d  %s%n", e.getValue(), e.getKey()));
                }
            } else if (cmd.equals("--queue")) {
                System.out.println("\n=== PENDING HANDLER MESSAGES ===");
                for (int q : findByClass("=android.os.MessageQueue")) {
                    int msg = fieldNode(q, "mMessages");
                    if (msg < 0) continue;
                    int n = 0;
                    long headWhen = fieldLong(msg, "when");
                    TreeMap<String, long[]> agg = new TreeMap<>();   // key -> {count, minWhen, maxWhen}
                    TreeMap<String, HashSet<Long>> whens = new TreeMap<>();
                    while (msg >= 0 && n < 20000) {
                        long w = fieldLong(msg, "when");
                        int cb = fieldNode(msg, "callback");
                        String key;
                        if (cb >= 0) {
                            key = nodeName(cb);
                        } else {
                            int ob = fieldNode(msg, "obj");
                            key = "what=" + fieldLong(msg, "what") + (ob < 0 ? "" : " obj=" + nodeName(ob));
                        }
                        int tg = fieldNode(msg, "target");
                        key += "   [" + (tg < 0 ? "no target" : nodeName(tg)) + "]";
                        long[] a = agg.computeIfAbsent(key, z -> new long[]{0, Long.MAX_VALUE, Long.MIN_VALUE});
                        a[0]++;
                        a[1] = Math.min(a[1], w);
                        a[2] = Math.max(a[2], w);
                        whens.computeIfAbsent(key, z -> new HashSet<>()).add(w);
                        n++;
                        msg = fieldNode(msg, "next");
                    }
                    System.out.printf("%n  %s @0x%x : %d pending%s%n", nodeName(q), nIdList[q], n,
                            n >= 20000 ? " (truncated)" : "");
                    final long base = headWhen;
                    agg.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(40)
                            .forEach(e -> System.out.printf("   %5d  due +%d..+%d ms  (%d distinct)  %s%n",
                                    e.getValue()[0], e.getValue()[1] - base, e.getValue()[2] - base,
                                    whens.get(e.getKey()).size(), e.getKey()));
                }
            } else if (cmd.equals("--remaining")) {
                String sub = args[++ai];
                int[] par = reachWithout(findByClasses(sub));
                long total = 0;
                TreeMap<String, long[]> byClass = new TreeMap<>();
                for (int i = 0; i < N; i++) {
                    if (par[i] == -2) continue;
                    total += nSize[i];
                    long[] arr = byClass.computeIfAbsent(nodeName(i).replaceAll("\\[[0-9]+\\]", "[]"), z -> new long[2]);
                    arr[0]++; arr[1] += nSize[i];
                }
                System.out.printf("%n=== STILL REACHABLE WITHOUT %s: %s ===%n", sub, hs(total));
                byClass.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1])).limit(35)
                        .forEach(e -> System.out.printf("   %10s  %,9d  %s%n", hs(e.getValue()[1]), e.getValue()[0], e.getKey()));
            } else if (cmd.equals("--histo")) {
                int lim = Integer.parseInt(args[++ai]);
                TreeMap<String, long[]> byClass = new TreeMap<>();
                for (int i = 0; i < N; i++) {
                    if (dfn[i] == 0) continue;
                    long[] arr = byClass.computeIfAbsent(nodeName(i).replaceAll("\\[[0-9]+\\]", "[]"), z -> new long[2]);
                    arr[0]++; arr[1] += nSize[i];
                }
                System.out.println("\n=== REACHABLE HISTOGRAM (count, shallow, class) ===");
                byClass.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1])).limit(lim > 0 ? lim : Long.MAX_VALUE)
                        .forEach(e -> System.out.printf("%d\t%d\t%s%n", e.getValue()[0], e.getValue()[1], e.getKey()));
            } else if (cmd.equals("--bitmaps")) {
                String sub = args[++ai];
                boolean dropping = !sub.equals("-");
                int[] par = dropping ? reachWithout(findByClasses(sub)) : null;
                long[] bytes = new long[2], count = new long[2], recycled = new long[2];
                TreeMap<String, long[]> dims = new TreeMap<>();
                for (int o : findByClass("=android.graphics.Bitmap")) {
                    if (dfn[o] == 0) continue;
                    int k = par == null || par[o] != -2 ? 1 : 0;
                    if (fieldValue(o, "mRecycled").equals("true")) { recycled[k]++; continue; }
                    long w = fieldLong(o, "mWidth"), h = fieldLong(o, "mHeight");
                    count[k]++; bytes[k] += w * h * 4;
                    if (k == 0 || !dropping) {
                        long[] a = dims.computeIfAbsent(w + "x" + h, z -> new long[2]);
                        a[0]++; a[1] += w * h * 4;
                    }
                }
                System.out.println("\n=== BITMAP PIXELS (native, estimated as width*height*4) ===");
                if (dropping) System.out.printf("  freed by dropping %s: %,d bitmaps, %s (+%d recycled)%n", sub, count[0], hs(bytes[0]), recycled[0]);
                System.out.printf("  %s: %,d bitmaps, %s (+%d recycled)%n", dropping ? "still reachable" : "reachable", count[1], hs(bytes[1]), recycled[1]);
                dims.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1])).limit(15)
                        .forEach(e -> System.out.printf("   %10s  %,7d  %s%n", hs(e.getValue()[1]), e.getValue()[0], e.getKey()));
            } else if (cmd.equals("--elems")) {
                int oid = (int) Long.parseLong(args[++ai].replace("0x", ""), 16);
                int o = id2idx.get(oid);
                int arr = backingArray(o);
                if (arr < 0) { System.out.println("not an Object[] or a known collection"); continue; }
                TreeMap<String, long[]> agg = new TreeMap<>();
                for (int e = 0; e < nCount[arr]; e++) {
                    int ei = arrayElem(arr, e);
                    long[] a = agg.computeIfAbsent(ei < 0 ? "null" : nodeName(ei).replaceAll("\\[[0-9]+\\]", "[]"), z -> new long[2]);
                    a[0]++; if (ei >= 0) a[1] += retained[ei];
                }
                System.out.printf("%n=== ELEMENTS of %s @0x%x ===%n", nodeName(arr), nIdList[arr]);
                agg.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(60)
                        .forEach(e -> System.out.printf("  %6d  ret=%10s  %s%n", e.getValue()[0], hs(e.getValue()[1]), e.getKey()));
            } else if (cmd.equals("--keys")) {
                String spec = args[++ai];
                List<Integer> objs = spec.startsWith("0x")
                        ? List.of(id2idx.get((int) Long.parseLong(spec.substring(2), 16))) : findByClasses(spec);
                for (int o : objs.subList(0, Math.min(5, objs.size()))) {
                    int map = fieldNode(o, "mMap");
                    if (map < 0) map = o;
                    int table = fieldNode(map, "table");
                    if (table < 0 || nKind[table] != 1) { System.out.printf("%n%s @0x%x is not a hash map%n", nodeName(o), nIdList[o]); continue; }
                    TreeMap<String, Integer> count = new TreeMap<>();
                    HashMap<String, String> example = new HashMap<>();
                    int total = 0;
                    for (int e = 0; e < nCount[table]; e++) {
                        for (int node = arrayElem(table, e), g = 0; node >= 0 && g < 1_000_000; node = fieldNode(node, "next"), g++) {
                            int k = fieldNode(node, "key");
                            String key = k < 0 ? "null" : Objects.requireNonNullElse(decodeString(k), nodeName(k));
                            String pattern = key.replaceAll("-?[0-9]+", "#");
                            count.merge(pattern, 1, Integer::sum);
                            example.putIfAbsent(pattern, key + " = " + fieldValue(node, "value"));
                            total++;
                        }
                    }
                    String file = fieldValue(o, "mFile.path");
                    System.out.printf("%n=== KEYS of %s @0x%x%s: %,d keys, %d patterns ===%n", nodeName(o), nIdList[o],
                            file.startsWith("\"") ? " " + file : "", total, count.size());
                    count.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(25)
                            .forEach(en -> System.out.printf("  %7d  %-40s e.g. %s%n", en.getValue(), en.getKey(), example.get(en.getKey())));
                }
            } else if (cmd.equals("--ncobs")) {
                int min = Integer.parseInt(args[++ai]);
                HashMap<Integer, String> idName = new HashMap<>();
                for (ClassMeta m : classes.values()) {
                    if (!"org.telegram.messenger.NotificationCenter".equals(m.name)) continue;
                    staticInts(m).forEach((name, value) -> { if (!name.equals("totalEvents") && !name.startsWith("$")) idName.merge(value, name, (x, y) -> x + "|" + y); });
                }
                List<Integer> centers = findByClass("=org.telegram.messenger.NotificationCenter");
                int[] withoutCenters = reachWithout(centers);
                System.out.println("\n=== NOTIFICATIONCENTER OBSERVERS (lists with >= " + min + ") ===");
                for (int nc : centers) {
                    System.out.printf("%n  account %d  @0x%x  retained=%s%n", fieldLong(nc, "currentAccount"), nIdList[nc], hs(retained[nc]));
                    LinkedHashSet<Integer> onlyHere = new LinkedHashSet<>();
                    for (String fld : new String[]{"observers", "removeAfterBroadcast", "addAfterBroadcast"}) {
                        int sa = fieldNode(nc, fld);
                        int keys = fieldNode(sa, "mKeys"), vals = fieldNode(sa, "mValues");
                        if (keys < 0 || vals < 0) continue;
                        long size = fieldLong(sa, "mSize");
                        long total = 0;
                        ArrayList<String> lines = new ArrayList<>();
                        for (int k = 0; k < size; k++) {
                            int key = b.getInt(nOff[keys] + k * 4);
                            int list = arrayElem(vals, k);
                            int arr = backingArray(list);
                            if (arr < 0) continue;
                            long n = fieldLong(list, "size");
                            total += n;
                            TreeMap<String, Integer> agg = new TreeMap<>();
                            for (int e = 0; e < n; e++) {
                                int ei = arrayElem(arr, e);
                                String nm = ei < 0 ? "null" : nodeName(ei);
                                if (nm.equals("org.telegram.messenger.NotificationCenter$WeakObserversGroupImpl")) {
                                    nm = "weak " + fieldValue(ei, "reference.referent");
                                } else if (ei >= 0 && withoutCenters[ei] == -2) {
                                    onlyHere.add(ei);
                                    nm += " (only here)";
                                }
                                agg.merge(nm, 1, Integer::sum);
                            }
                            if (n < min) continue;
                            StringBuilder sb2 = new StringBuilder();
                            agg.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(6)
                                    .forEach(e -> sb2.append(e.getValue()).append("x ").append(e.getKey()).append("  "));
                            lines.add(String.format("    %5d  %-38s %s", n, key + " " + idName.getOrDefault(key, "?"), sb2));
                        }
                        if (total == 0 && !fld.equals("observers")) continue;
                        System.out.printf("    %s: %d ids, %d observers%n", fld, size, total);
                        lines.forEach(System.out::println);
                    }
                    for (int o : onlyHere)
                        System.out.printf("    alive only through NotificationCenter: %s @0x%x  retained=%s%n", nodeName(o), nIdList[o], hs(retained[o]));
                }
            } else if (cmd.equals("--animcb")) {
                for (int ah : findByClass("=android.animation.AnimationHandler")) {
                    int list = fieldNode(ah, "mAnimationCallbacks");
                    int arr = backingArray(list);
                    if (arr < 0) continue;
                    long n = fieldLong(list, "size");
                    int paused = fieldNode(ah, "mPausedAnimators");
                    System.out.printf("%n=== AnimationHandler @0x%x: %d frame callbacks, %d paused animators ===%n",
                            nIdList[ah], n, paused < 0 ? 0 : fieldLong(paused, "size"));
                    TreeMap<String, long[]> agg = new TreeMap<>();
                    for (int e = 0; e < n; e++) {
                        int cb = arrayElem(arr, e);
                        if (cb < 0) { agg.computeIfAbsent("null (removed, not yet cleaned up)", z -> new long[]{0, Long.MAX_VALUE, -1})[0]++; continue; }
                        StringBuilder k = new StringBuilder(nodeName(cb));
                        int target = fieldNode(cb, "mTarget");
                        if (target >= 0) k.append(" -> ").append(nodeName(target));
                        int ul = backingArray(fieldNode(cb, "mUpdateListeners"));
                        if (ul >= 0 && nCount[ul] > 0 && arrayElem(ul, 0) >= 0) k.append("  update=").append(nodeName(arrayElem(ul, 0)));
                        int ls = backingArray(fieldNode(cb, "mListeners"));
                        if (ls >= 0 && nCount[ls] > 0 && arrayElem(ls, 0) >= 0) k.append("  listener=").append(nodeName(arrayElem(ls, 0)));
                        long dur = fieldLong(cb, "mDuration"), rep = fieldLong(cb, "mRepeatCount");
                        if (dur >= 0) k.append("  ").append(dur).append("ms").append(rep < 0 ? " infinite" : rep > 0 ? " x" + (rep + 1) : "");
                        long[] a = agg.computeIfAbsent(k.toString(), z -> new long[]{0, Long.MAX_VALUE, -1});
                        a[0]++;
                        long start = fieldLong(cb, "mStartTime"), last = fieldLong(cb, "mLastFrameTime");
                        if (start > 0 && last >= start) { a[1] = Math.min(a[1], last - start); a[2] = Math.max(a[2], last - start); }
                    }
                    agg.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(40)
                            .forEach(e -> {
                                long[] a = e.getValue();
                                String age = a[2] < 0 ? "" : "  running " + (a[1] == a[2] ? ageString(a[2]) : ageString(a[1]) + ".." + ageString(a[2]));
                                System.out.printf("  %5d  %s%s%n", a[0], e.getKey(), age);
                            });
                }
            } else if (cmd.equals("--report")) {
                report(args);
            }
        }
    }

    static int[] domChildHead, domChildTgt;

    /** Children of the dominator tree, in CSR form. Built once, on demand. */
    static void buildDomChildren() {
        if (domChildHead != null) return;
        int[] deg = new int[N + 2];
        for (int i = 1; i <= reachCount; i++) { int w = vertexArr[i]; if (w != SUPER) deg[idom[w]]++; }
        domChildHead = new int[N + 2];
        int acc = 0;
        for (int i = 0; i <= SUPER; i++) { domChildHead[i] = acc; acc += deg[i]; }
        domChildHead[SUPER + 1] = acc;
        domChildTgt = new int[acc];
        int[] cur = new int[N + 2];
        System.arraycopy(domChildHead, 0, cur, 0, N + 2);
        for (int i = 1; i <= reachCount; i++) { int w = vertexArr[i]; if (w != SUPER) domChildTgt[cur[idom[w]]++] = w; }
    }

    static long retainsFloor;

    /**
     * Prints what an object exclusively holds, biggest first. Children below retainsFloor are
     * summed into one line rather than printed - without that a multi-MB object buries its own
     * answer under thousands of 32-byte leaves.
     */
    static void retainsTree(int o, int levels, int depth) {
        if (depth >= levels) return;
        int s = domChildHead[o], e = domChildHead[o + 1];
        Integer[] kids = new Integer[e - s];
        for (int i = s; i < e; i++) kids[i - s] = domChildTgt[i];
        Arrays.sort(kids, (x, y) -> Long.compare(retained[y], retained[x]));
        int shown = 0;
        long rest = 0;
        for (int k = 0; k < kids.length; k++) {
            int c = kids[k];
            if (shown < 8 && retained[c] >= retainsFloor) {
                System.out.printf("  %s%10s  %s @0x%x%n", "  ".repeat(depth), hs(retained[c]), nodeName(c), nIdList[c]);
                retainsTree(c, levels, depth + 1);
                shown++;
            } else {
                rest += retained[c];
            }
        }
        if (shown < kids.length)
            System.out.printf("  %s%10s  ... %,d more%n", "  ".repeat(depth), hs(rest), kids.length - shown);
    }

    static void ancestors(int o, int levels, int depth, HashSet<Integer> seen) {
        if (o < 0 || depth > levels || !seen.add(o)) return;
        int deg = predHead[o + 1] - predHead[o];
        System.out.printf("%s%s @0x%x  ret=%s  (%d inbound)%n", "  ".repeat(depth), nodeName(o), nIdList[o], hs(retained[o]), deg);
        int shown = 0;
        for (int e = predHead[o]; e < predHead[o + 1] && shown < 6; e++) {
            int pp = predTgt[e];
            if (pp == SUPER) { System.out.printf("%s<- GC-ROOT%n", "  ".repeat(depth + 1)); continue; }
            System.out.printf("%s<-%s%n", "  ".repeat(depth + 1), edgeLabel(pp, o));
            ancestors(pp, levels, depth + 1, seen);
            shown++;
        }
    }

    /** Decodes a java.lang.String instance; handles both the value[] and ART inline layouts. */
    static String decodeString(int node) {
        if (node < 0 || nKind[node] != 0) return null;
        ClassMeta m = classes.get(nClass[node]);
        if (m == null || !"java.lang.String".equals(m.name)) return null;
        int count = -1, valNode = -1;
        ClassMeta cur = m; int off = 0;
        while (cur != null) {
            for (int f = 0; f < cur.fNameId.length; f++) {
                int t = cur.fType[f];
                String fn = strings.getOrDefault(cur.fNameId[f], "?");
                if (fn.equals("count") && t == 10) count = b.getInt(nOff[node] + off);
                if (fn.equals("value") && t == 2) { int v = b.getInt(nOff[node] + off); valNode = v == 0 ? -1 : id2idx.get(v); }
                off += typeSize(t);
            }
            cur = cur.superId == 0 ? null : classes.get(cur.superId);
        }
        if (valNode >= 0 && nKind[valNode] == 2 && nOff[valNode] > 0) {
            int t = nClass[valNode]; int n = nCount[valNode];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(n, 200); i++)
                sb.append(t == 5 ? (char) b.getShort(nOff[valNode] + i * 2) : (char) (b.get(nOff[valNode] + i) & 0xff));
            return sb.toString();
        }
        if (count >= 0) {
            int len = count & 0x7fffffff;
            int dataOff = nOff[node] + off;
            int avail = nCount[node] - off;
            if (avail <= 0) return "";
            StringBuilder sb = new StringBuilder();
            int chars = Math.min(len, 200);
            if (avail >= len * 2) for (int i = 0; i < chars; i++) sb.append((char) b.getShort(dataOff + i * 2));
            else for (int i = 0; i < Math.min(chars, avail); i++) sb.append((char) (b.get(dataOff + i) & 0xff));
            return sb.toString();
        }
        return null;
    }

    /** Reads one int/long field by name; returns -1 if absent or not an integral type. */
    static long fieldLong(int obj, String fname) {
        if (obj < 0 || nKind[obj] != 0) return -1;
        ClassMeta cur = classes.get(nClass[obj]);
        int off = 0;
        while (cur != null) {
            for (int f = 0; f < cur.fNameId.length; f++) {
                int t = cur.fType[f];
                if (strings.getOrDefault(cur.fNameId[f], "?").equals(fname)) {
                    if (t == 11) return b.getLong(nOff[obj] + off);
                    if (t == 10) return b.getInt(nOff[obj] + off);
                    return -1;
                }
                off += typeSize(t);
            }
            cur = cur.superId == 0 ? null : classes.get(cur.superId);
        }
        return -1;
    }

    /** Follows one object-typed field by name, returning the target node index or -1. */
    static int fieldNode(int obj, String fname) {
        if (obj < 0 || nKind[obj] != 0) return -1;
        ClassMeta cur = classes.get(nClass[obj]);
        int off = 0;
        while (cur != null) {
            for (int f = 0; f < cur.fNameId.length; f++) {
                int t = cur.fType[f];
                if (strings.getOrDefault(cur.fNameId[f], "?").equals(fname) && t == 2) {
                    int v = b.getInt(nOff[obj] + off);
                    return v == 0 ? -1 : id2idx.get(v);
                }
                off += typeSize(t);
            }
            cur = cur.superId == 0 ? null : classes.get(cur.superId);
        }
        return -1;
    }

    static final Set<String> BOXES = Set.of("java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double");

    /** File offset and hprof type of a declared or inherited field, or null if the instance has none. */
    static int[] fieldSlot(int obj, String fname) {
        if (obj < 0 || nKind[obj] != 0) return null;
        ClassMeta cur = classes.get(nClass[obj]);
        int off = 0;
        while (cur != null) {
            for (int f = 0; f < cur.fNameId.length; f++) {
                int t = cur.fType[f];
                if (strings.getOrDefault(cur.fNameId[f], "?").equals(fname))
                    return off + typeSize(t) <= nCount[obj] ? new int[]{nOff[obj] + off, t} : null;
                off += typeSize(t);
            }
            cur = cur.superId == 0 ? null : classes.get(cur.superId);
        }
        return null;
    }

    /**
     * Printable value of a field path such as "mCallback.referent" or "specs.indicatorSize":
     * a class name or "null" for references, the quoted text for Strings, the number or flag
     * for primitives. Reference fields are read directly, so this also sees through weak/soft
     * referents.
     */
    static String fieldValue(int obj, String path) {
        String[] parts = path.split("\\.");
        for (int i = 0; ; i++) {
            int bracket = parts[i].indexOf('[');
            if (bracket > 0 && parts[i].endsWith("]")) {
                int arr = fieldNode(obj, parts[i].substring(0, bracket));
                int idx = Integer.parseInt(parts[i].substring(bracket + 1, parts[i].length() - 1));
                if (arr < 0 || nKind[arr] != 1) return "<no such field>";
                if (idx >= nCount[arr]) return "<out of bounds>";
                int el = arrayElem(arr, idx);
                if (el < 0) return "null";
                if (i == parts.length - 1) return nodeName(el) + " @0x" + Integer.toHexString(nIdList[el]);
                obj = el;
                continue;
            }
            int[] s = fieldSlot(obj, parts[i]);
            if (s == null) return "<no such field>";
            int p = s[0];
            if (s[1] == 2) {
                int v = b.getInt(p);
                if (v == 0) return "null";
                int ti = id2idx.get(v);
                if (ti < 0) return "?";
                if (i == parts.length - 1) {
                    String str = decodeString(ti);
                    if (str != null) return "\"" + str + "\"";
                    String cls = nodeName(ti);
                    return BOXES.contains(cls) ? fieldValue(ti, "value") : cls;
                }
                obj = ti;
                continue;
            }
            if (i != parts.length - 1) return "<no such field>";
            switch (s[1]) {
                case 4: return String.valueOf(b.get(p) != 0);
                case 5: return String.valueOf((char) b.getShort(p));
                case 6: return String.valueOf(b.getFloat(p));
                case 7: return String.valueOf(b.getDouble(p));
                case 8: return String.valueOf(b.get(p));
                case 9: return String.valueOf(b.getShort(p));
                case 10: return String.valueOf(b.getInt(p));
                case 11: return String.valueOf(b.getLong(p));
            }
            return "?";
        }
    }

    /** Static int fields of a class by name, re-read from the class dump record on demand. */
    static LinkedHashMap<String, Integer> staticInts(ClassMeta m) {
        LinkedHashMap<String, Integer> r = new LinkedHashMap<>();
        int p = m.staticsPos;
        int ns = b.getShort(p) & 0xffff;
        p += 2;
        for (int i = 0; i < ns; i++) {
            int nameId = b.getInt(p);
            int t = b.get(p + idSize) & 0xff;
            p += idSize + 1;
            if (t == 10) r.put(strings.getOrDefault(nameId, "?"), b.getInt(p));
            p += typeSize(t);
        }
        return r;
    }

    /** BFS from the GC roots that never enters the dropped objects; par[i] == -2 means unreachable. */
    static int[] reachWithout(List<Integer> dropped) {
        boolean[] drop = new boolean[N + 1];
        for (int o : dropped) drop[o] = true;
        int[] par = new int[N + 1];
        Arrays.fill(par, -2);
        int[] q = new int[N + 2]; int qh = 0, qt = 0;
        q[qt++] = SUPER; par[SUPER] = -1;
        while (qh < qt) {
            int u = q[qh++];
            for (int e = eHead[u]; e < eHead[u + 1]; e++) {
                int v = eTgt[e];
                if (v < 0 || par[v] != -2 || drop[v]) continue;
                par[v] = u; q[qt++] = v;
            }
        }
        return par;
    }

    /** Elements of an ArrayList/SparseArray/ArrayMap/CopyOnWriteArrayList as their backing Object[]; -1 otherwise. */
    static int backingArray(int o) {
        if (o < 0 || nKind[o] == 1) return o;
        for (String f : new String[]{"elementData", "mValues", "mArray", "array"}) {
            int a = fieldNode(o, f);
            if (a >= 0 && nKind[a] == 1) return a;
        }
        return -1;
    }

    /** Referenced element i of an Object[] as a node index, -1 for null or dangling. */
    static int arrayElem(int arr, int i) {
        int v = b.getInt(nOff[arr] + i * idSize);
        return v == 0 ? -1 : id2idx.get(v);
    }

    static String ageString(long ms) {
        if (ms < 120_000) return ms / 1000 + "s";
        if (ms < 7_200_000) return ms / 60_000 + "m";
        return String.format("%.1fh", ms / 3_600_000.0);
    }

    static String rootName(int k) {
        switch (k) {
            case 0x01: return "JNI_GLOBAL"; case 0x02: return "JNI_LOCAL"; case 0x03: return "JAVA_FRAME";
            case 0x04: return "NATIVE_STACK"; case 0x05: return "STICKY_CLASS"; case 0x06: return "THREAD_BLOCK";
            case 0x07: return "MONITOR_USED"; case 0x08: return "THREAD_OBJECT"; case 0x89: return "INTERNED_STRING";
            case 0x8a: return "FINALIZING"; case 0x8b: return "DEBUGGER"; case 0x8c: return "REFERENCE_CLEANUP";
            case 0x8d: return "VM_INTERNAL"; case 0x8e: return "JNI_MONITOR"; case 0x90: return "UNREACHABLE";
            case 0xff: return "UNKNOWN";
        }
        return "ROOT_0x" + Integer.toHexString(k);
    }

    static void dumpFields(int i) {
        if (i < 0) { System.out.println("not found"); return; }
        System.out.printf("%n=== FIELDS of %s @0x%x retained=%s ===%n", nodeName(i), nIdList[i], hs(retained[i]));
        ClassMeta m = classes.get(nClass[i]);
        int base = nOff[i];
        ClassMeta cur = m;
        int off = 0;
        while (cur != null) {
            for (int f = 0; f < cur.fNameId.length; f++) {
                int t = cur.fType[f];
                String nm = strings.getOrDefault(cur.fNameId[f], "?");
                String val;
                int p = base + off;
                switch (t) {
                    case 2: { int v = b.getInt(p); int ti = id2idx.get(v);
                              val = v == 0 ? "null" : (ti >= 0 ? nodeName(ti) + " @0x" + Integer.toHexString(v) : "@0x" + Integer.toHexString(v)); break; }
                    case 4: val = String.valueOf(b.get(p) != 0); break;
                    case 8: val = String.valueOf(b.get(p)); break;
                    case 5: val = String.valueOf((char) b.getShort(p)); break;
                    case 9: val = String.valueOf(b.getShort(p)); break;
                    case 10: val = String.valueOf(b.getInt(p)); break;
                    case 6: val = String.valueOf(b.getFloat(p)); break;
                    case 11: val = String.valueOf(b.getLong(p)); break;
                    case 7: val = String.valueOf(b.getDouble(p)); break;
                    default: val = "?";
                }
                System.out.printf("  %-40s %s%n", (cur == m ? "" : cur.name + "#") + nm, val);
                off += typeSize(t);
            }
            cur = cur.superId == 0 ? null : classes.get(cur.superId);
        }
    }

    static void usage() {
        System.out.println("""
            HprofAnalyze - Android (ART) heap dump analyzer

              java -Xmx4g Tools/HprofAnalyze.java <dump.hprof> [command...]

            No command prints the overview report. Commands may be chained.

              --count      <class>                  instances, shallow + retained
              --band       <lo> <hi>                classes with instance count in [lo,hi]
              --classinfo  <substr>                 class -> superclass chain
              --who        <class>                  inbound refs by holder.field, with size
              --dom        <class>                  immediate dominators, with retained size
              --holders    <0xid>                   every inbound ref of one object
              --ancestors  <0xid> <levels>          reference tree walked backwards
              --retains    <0xid> <levels>          dominator tree: what one object holds
              --paths      <class> <n>              shortest GC-root path, n biggest
              --fields     <0xid>                   all field values of one object
              --fieldstats <class> <f1,a.b,x+y>     field value distribution, paths allowed
              --strings    <class>                  decoded String fields
              --roottype   <class>                  GC root kind per instance
              --rootstats                           all GC roots by kind and class
              --jni                                 objects held by JNI global refs
              --jniref     <class>                  field shapes of JNI-global instances
              --freeif     <classes>                bytes freed if these were released
              --survivors  <drop> <check>           distinct paths still rooting <check>
              --remaining  <classes>                histogram of what is left without them
              --histo      <n>                      reachable histogram, TSV, for diffing dumps
              --bitmaps    <classes|->              bitmap pixels those free vs keep
              --elems      <0xid>                   elements of an array or collection
              --keys       <class|0xid>             HashMap/SharedPreferences keys by pattern
              --ncobs      <min>                    NotificationCenter observers per event id,
                                                    (only here) = kept alive by nothing else
              --animcb                              running animators per AnimationHandler
              --queue                               pending Handler messages per queue
              --hooks                               installed Xposed hooks, by plugin

            <class> is a substring; prefix '=' for exact, '^' for class-or-subclass;
            suffix @path=value, @path~text, @path>n or @path<n filters instances by a field.
            <classes> is a comma-separated list of <class>.
            Weak/soft/phantom referent edges are not followed.
            """);
    }

    static int readId() { return b.getInt(); }

    static int[] pass1() {
        b.position(headerEnd);
        int nInst = 0, nOA = 0, nPA = 0;
        while (b.remaining() > 9) {
            int tag = b.get() & 0xff;
            b.getInt();
            long rlen = b.getInt() & 0xffffffffL;
            int end = (int) (b.position() + rlen);
            switch (tag) {
                case 0x01: { // string
                    int sid = readId();
                    byte[] bytes = new byte[(int) rlen - idSize];
                    b.get(bytes);
                    strings.put(sid, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    break;
                }
                case 0x02: { // load class
                    b.getInt(); int cid = readId(); b.getInt(); int nid = readId();
                    classNameId.put(cid, nid);
                    break;
                }
                case 0x0c: case 0x1c: { // heap dump (segment)
                    while (b.position() < end) {
                        int st = b.get() & 0xff;
                        if (!subRecordPass1(st)) { b.position(end); break; }
                        int k = lastKind;
                        if (k == 0) nInst++; else if (k == 1) nOA++; else if (k == 2) nPA++;
                    }
                    break;
                }
                default: break;
            }
            b.position(end);
        }
        return new int[]{nInst, nOA, nPA};
    }

    static int lastKind;

    // returns false on unknown tag
    static boolean subRecordPass1(int st) {
        lastKind = -1;
        switch (st) {
            case 0xff: readId(); return true;                       // ROOT_UNKNOWN
            case 0x01: { int o = readId(); readId(); root(o, st); return true; }  // JNI GLOBAL
            case 0x02: { int o = readId(); b.getInt(); b.getInt(); root(o, st); return true; }
            case 0x03: { int o = readId(); b.getInt(); b.getInt(); root(o, st); return true; }
            case 0x04: { int o = readId(); b.getInt(); root(o, st); return true; }
            case 0x05: { int o = readId(); root(o, st); return true; }             // STICKY CLASS
            case 0x06: { int o = readId(); b.getInt(); root(o, st); return true; }
            case 0x07: { int o = readId(); root(o, st); return true; }
            case 0x08: { int o = readId(); b.getInt(); b.getInt(); root(o, st); return true; }
            case 0x89: case 0x8a: case 0x8b: case 0x8c: case 0x8d: case 0x90:
                { int o = readId(); root(o, st); return true; }
            case 0x8e: { int o = readId(); b.getInt(); b.getInt(); root(o, st); return true; }
            case 0xfe: { b.getInt(); int nid = readId(); return true; }            // HEAP_DUMP_INFO
            case 0x20: { readClassDump(); return true; }
            case 0x21: { readId(); b.getInt(); readId(); int nb = b.getInt(); b.position(b.position() + nb); lastKind = 0; return true; }
            case 0x22: { readId(); b.getInt(); int n = b.getInt(); readId(); b.position(b.position() + n * idSize); lastKind = 1; return true; }
            case 0x23: { readId(); b.getInt(); int n = b.getInt(); int t = b.get() & 0xff; b.position(b.position() + n * typeSize(t)); lastKind = 2; return true; }
            case 0xc3: { readId(); b.getInt(); b.getInt(); b.get(); lastKind = 2; return true; }
            default:
                System.err.println("unknown sub tag 0x" + Integer.toHexString(st) + " at " + b.position());
                return false;
        }
    }

    static void root(int obj, int kind) { if (obj != 0) { rootIds.add(obj); rootKinds.add(kind); } }

    static void readClassDump() {
        ClassMeta m = new ClassMeta();
        m.id = readId();
        b.getInt();
        m.superId = readId();
        readId(); readId(); readId(); readId(); readId();
        m.instanceSize = b.getInt();
        int cp = b.getShort() & 0xffff;
        for (int i = 0; i < cp; i++) { b.getShort(); int t = b.get() & 0xff; b.position(b.position() + typeSize(t)); }
        m.staticsPos = b.position();
        int ns = b.getShort() & 0xffff;
        IntList sv = new IntList(); ArrayList<String> sn = new ArrayList<>();
        for (int i = 0; i < ns; i++) {
            int nameId = readId(); int t = b.get() & 0xff;
            if (t == 2) { int v = readId(); if (v != 0) { sv.add(v); sn.add(strings.getOrDefault(nameId, "?")); } }
            else b.position(b.position() + typeSize(t));
        }
        m.staticObjVal = Arrays.copyOf(sv.a, sv.n);
        m.staticObjName = sn.toArray(new String[0]);
        int nf = b.getShort() & 0xffff;
        m.fNameId = new int[nf]; m.fType = new byte[nf];
        for (int i = 0; i < nf; i++) { m.fNameId[i] = readId(); m.fType[i] = b.get(); }
        Integer sid = classNameId.get(m.id);
        m.name = sid == null ? null : strings.get(sid);
        classes.put(m.id, m);
    }

    static int idxCursor = 0;
    static int curHeap = 0;

    static void pass2() {
        // class objects first
        for (ClassMeta m : classes.values()) {
            int i = idxCursor++;
            m.nodeIdx = i;
            id2idx.put(m.id, i);
            nClass[i] = m.id; nKind[i] = 3; nIdList[i] = m.id;
            int sz = 0;
            sz += m.staticObjVal.length * idSize;
            nSize[i] = 32 + sz;
        }
        b.position(headerEnd);
        while (b.remaining() > 9) {
            int tag = b.get() & 0xff;
            b.getInt();
            long rlen = b.getInt() & 0xffffffffL;
            int end = (int) (b.position() + rlen);
            if (tag == 0x0c || tag == 0x1c) {
                while (b.position() < end) {
                    int st = b.get() & 0xff;
                    if (!subRecordPass2(st)) { b.position(end); break; }
                }
            }
            b.position(end);
        }
    }

    static boolean subRecordPass2(int st) {
        switch (st) {
            case 0xfe: { curHeap = b.getInt(); int nid = readId();
                if (curHeap >= 0 && curHeap < 256) heapNames[curHeap] = strings.get(nid); return true; }
            case 0x21: {
                int oid = readId(); b.getInt(); int cid = readId(); int nb = b.getInt();
                int i = idxCursor++;
                id2idx.put(oid, i);
                nClass[i] = cid; nKind[i] = 0; nOff[i] = b.position(); nIdList[i] = oid;
                ClassMeta m = classes.get(cid);
                nSize[i] = m != null && m.instanceSize > 0 ? m.instanceSize : nb + 16;
                nCount[i] = nb;
                nHeap[i] = (byte) curHeap;
                b.position(b.position() + nb);
                return true;
            }
            case 0x22: {
                int oid = readId(); b.getInt(); int n = b.getInt(); int acid = readId();
                int i = idxCursor++;
                id2idx.put(oid, i);
                nClass[i] = acid; nKind[i] = 1; nOff[i] = b.position(); nCount[i] = n; nIdList[i] = oid;
                nSize[i] = 16 + n * idSize;
                nHeap[i] = (byte) curHeap;
                b.position(b.position() + n * idSize);
                return true;
            }
            case 0x23: {
                int oid = readId(); b.getInt(); int n = b.getInt(); int t = b.get() & 0xff;
                int i = idxCursor++;
                id2idx.put(oid, i);
                nClass[i] = t; nKind[i] = 2; nOff[i] = b.position(); nCount[i] = n; nIdList[i] = oid;
                nSize[i] = 16 + n * typeSize(t);
                nHeap[i] = (byte) curHeap;
                b.position(b.position() + n * typeSize(t));
                return true;
            }
            case 0xc3: {
                int oid = readId(); b.getInt(); int n = b.getInt(); int t = b.get() & 0xff;
                int i = idxCursor++;
                id2idx.put(oid, i);
                nClass[i] = t; nKind[i] = 2; nOff[i] = 0; nCount[i] = n; nIdList[i] = oid;
                nSize[i] = 16 + n * typeSize(t);
                nHeap[i] = (byte) curHeap;
                return true;
            }
            default: return subRecordPass1(st);
        }
    }

    static void resolveFieldLayouts() {
        for (ClassMeta m : classes.values()) {
            IntList offs = new IntList(); ArrayList<String> names = new ArrayList<>();
            int off = 0;
            ClassMeta cur = m;
            while (cur != null) {
                for (int i = 0; i < cur.fNameId.length; i++) {
                    int t = cur.fType[i];
                    if (t == 2) { offs.add(off); names.add(strings.getOrDefault(cur.fNameId[i], "?")); }
                    off += typeSize(t);
                }
                cur = cur.superId == 0 ? null : classes.get(cur.superId);
            }
            m.objFieldOff = Arrays.copyOf(offs.a, offs.n);
            m.objFieldName = names.toArray(new String[0]);
        }
    }

    static HashSet<Integer> weakRefClasses = new HashSet<>();

    static boolean isReferenceClass(int classObjId) {
        ClassMeta m = classes.get(classObjId);
        int g = 0;
        while (m != null && g++ < 20) {
            if ("java.lang.ref.Reference".equals(m.name)) return true;
            m = m.superId == 0 ? null : classes.get(m.superId);
        }
        return false;
    }

    static int[] eHead;   // size N+2 (last slot = super root)
    static int[] eTgt;
    static int SUPER;

    static void buildEdges() {
        SUPER = N;
        for (Integer cid : classes.keySet()) if (isReferenceClass(cid)) weakRefClasses.add(cid);
        System.err.println("reference-classes (referent edge dropped): " + weakRefClasses.size());
        int[] deg = new int[N + 2];
        // count
        for (int i = 0; i < N; i++) {
            if (nKind[i] == 0) {
                ClassMeta m = classes.get(nClass[i]);
                if (m == null) continue;
                int base = nOff[i];
                boolean isRef = weakRefClasses.contains(nClass[i]);
                int d = 0;
                for (int f = 0; f < m.objFieldOff.length; f++) {
                    int off = base + m.objFieldOff[f];
                    if (m.objFieldOff[f] + idSize > nCount[i]) continue;
                    if (isRef && "referent".equals(m.objFieldName[f])) continue;
                    if (b.getInt(off) != 0) d++;
                }
                deg[i] = d;
            } else if (nKind[i] == 1) {
                int base = nOff[i]; int d = 0;
                for (int e = 0; e < nCount[i]; e++) if (b.getInt(base + e * idSize) != 0) d++;
                deg[i] = d;
            } else if (nKind[i] == 3) {
                ClassMeta m = classes.get(nClass[i]);
                int d = 0;
                if (m != null) for (int v : m.staticObjVal) if (id2idx.get(v) >= 0) d++;
                if (m != null && m.superId != 0 && classes.containsKey(m.superId)) d++;
                deg[i] = d;
            }
        }
        HashSet<Integer> rootSet = new HashSet<>();
        for (int i = 0; i < rootIds.n; i++) { int idx = id2idx.get(rootIds.get(i)); if (idx >= 0) rootSet.add(idx); }
        for (int i = 0; i < N; i++) if (nKind[i] == 3) rootSet.add(i);
        deg[SUPER] = rootSet.size();

        eHead = new int[N + 2];
        int acc = 0;
        for (int i = 0; i <= SUPER; i++) { eHead[i] = acc; acc += deg[i]; }
        eHead[SUPER + 1] = acc;
        eTgt = new int[acc];
        int[] cur = new int[N + 2];
        System.arraycopy(eHead, 0, cur, 0, N + 2);

        for (int i = 0; i < N; i++) {
            if (nKind[i] == 0) {
                ClassMeta m = classes.get(nClass[i]);
                if (m == null) continue;
                int base = nOff[i];
                boolean isRef2 = weakRefClasses.contains(nClass[i]);
                for (int f = 0; f < m.objFieldOff.length; f++) {
                    if (m.objFieldOff[f] + idSize > nCount[i]) continue;
                    if (isRef2 && "referent".equals(m.objFieldName[f])) continue;
                    int v = b.getInt(base + m.objFieldOff[f]);
                    if (v == 0) continue;
                    int ti = id2idx.get(v);
                    eTgt[cur[i]++] = ti;
                }
            } else if (nKind[i] == 1) {
                int base = nOff[i];
                for (int e = 0; e < nCount[i]; e++) {
                    int v = b.getInt(base + e * idSize);
                    if (v == 0) continue;
                    eTgt[cur[i]++] = id2idx.get(v);
                }
            } else if (nKind[i] == 3) {
                ClassMeta m = classes.get(nClass[i]);
                if (m != null) {
                    for (int v : m.staticObjVal) { int ti = id2idx.get(v); if (ti >= 0) eTgt[cur[i]++] = ti; }
                    if (m.superId != 0) { ClassMeta sm = classes.get(m.superId); if (sm != null) eTgt[cur[i]++] = sm.nodeIdx; }
                }
            }
        }
        for (int r : rootSet) eTgt[cur[SUPER]++] = r;
        // -1 targets (dangling ids) -> point to SUPER-safe sentinel: replace with -1 handled in traversal
    }

    // ---------------- dominators (Lengauer-Tarjan) ----------------
    static int[] idom;
    static long[] retained;
    static int reachCount;
    static int[] pred;          // BFS parent (shortest path from GC root)
    static int[] predHead, predTgt;
    static int[] dfn, vertexArr, parentArr, semi, ancestor, label, pathBuf;

    static void computeDominators() {
        int n = N + 1;
        dfn = new int[n]; vertexArr = new int[n + 1]; parentArr = new int[n];
        semi = new int[n]; ancestor = new int[n]; label = new int[n];
        idom = new int[n]; pathBuf = new int[n];
        int[] bucketHead = new int[n]; int[] bucketNext = new int[n];
        Arrays.fill(ancestor, -1); Arrays.fill(bucketHead, -1);

        int[] stack = new int[n + 4]; int[] eptr = new int[n + 4];
        int sp = 0, cnt = 0;
        cnt++; dfn[SUPER] = cnt; vertexArr[cnt] = SUPER; semi[SUPER] = cnt; label[SUPER] = SUPER; parentArr[SUPER] = -1;
        stack[0] = SUPER; eptr[0] = eHead[SUPER];
        while (sp >= 0) {
            int u = stack[sp];
            if (eptr[sp] < eHead[u + 1]) {
                int v = eTgt[eptr[sp]++];
                if (v < 0 || dfn[v] != 0) continue;
                cnt++; dfn[v] = cnt; vertexArr[cnt] = v; semi[v] = cnt; label[v] = v; parentArr[v] = u;
                sp++; stack[sp] = v; eptr[sp] = eHead[v];
            } else sp--;
        }
        reachCount = cnt;

        // predecessor CSR over reachable nodes
        int[] pdeg = new int[n + 1];
        for (int u = 0; u <= SUPER; u++) {
            if (dfn[u] == 0) continue;
            for (int e = eHead[u]; e < eHead[u + 1]; e++) { int v = eTgt[e]; if (v >= 0 && dfn[v] != 0) pdeg[v]++; }
        }
        predHead = new int[n + 1];
        int acc = 0;
        for (int i = 0; i <= SUPER; i++) { predHead[i] = acc; acc += pdeg[i]; }
        predHead[SUPER + 1] = acc;
        predTgt = new int[acc];
        int[] pcur = new int[n + 1];
        System.arraycopy(predHead, 0, pcur, 0, n + 1);
        for (int u = 0; u <= SUPER; u++) {
            if (dfn[u] == 0) continue;
            for (int e = eHead[u]; e < eHead[u + 1]; e++) { int v = eTgt[e]; if (v >= 0 && dfn[v] != 0) predTgt[pcur[v]++] = u; }
        }

        for (int i = cnt; i >= 2; i--) {
            int w = vertexArr[i];
            for (int e = predHead[w]; e < predHead[w + 1]; e++) {
                int v = predTgt[e];
                if (dfn[v] == 0) continue;
                int u = eval(v);
                if (semi[u] < semi[w]) semi[w] = semi[u];
            }
            int sw = vertexArr[semi[w]];
            bucketNext[w] = bucketHead[sw]; bucketHead[sw] = w;
            int pw = parentArr[w];
            ancestor[w] = pw;
            for (int v = bucketHead[pw]; v != -1; ) {
                int nx = bucketNext[v];
                int u = eval(v);
                idom[v] = (semi[u] < semi[v]) ? u : pw;
                v = nx;
            }
            bucketHead[pw] = -1;
        }
        for (int i = 2; i <= cnt; i++) {
            int w = vertexArr[i];
            if (idom[w] != vertexArr[semi[w]]) idom[w] = idom[idom[w]];
        }
        idom[SUPER] = SUPER;

        retained = new long[n];
        for (int i = 1; i <= cnt; i++) { int u = vertexArr[i]; retained[u] = (u < N) ? nSize[u] : 0; }
        for (int i = cnt; i >= 2; i--) { int w = vertexArr[i]; retained[idom[w]] += retained[w]; }

        // BFS parents for shortest path to a GC root
        pred = new int[n];
        Arrays.fill(pred, -2);
        int[] q = new int[cnt + 4];
        int qh = 0, qt = 0;
        q[qt++] = SUPER; pred[SUPER] = -1;
        while (qh < qt) {
            int u = q[qh++];
            for (int e = eHead[u]; e < eHead[u + 1]; e++) {
                int v = eTgt[e];
                if (v < 0 || pred[v] != -2) continue;
                pred[v] = u; q[qt++] = v;
            }
        }
    }

    static int eval(int v) {
        if (ancestor[v] == -1) return label[v];
        int p = 0, x = v;
        while (ancestor[x] != -1) { pathBuf[p++] = x; x = ancestor[x]; }
        for (int i = p - 1; i >= 0; i--) {
            int y = pathBuf[i];
            int a = ancestor[y];
            if (ancestor[a] != -1) {
                if (semi[label[a]] < semi[label[y]]) label[y] = label[a];
                ancestor[y] = ancestor[a];
            }
        }
        return label[v];
    }

    // field name that makes u point at v (best effort)
    static String edgeLabel(int u, int v) {
        if (u == SUPER) return "";
        if (nKind[u] == 0) {
            ClassMeta m = classes.get(nClass[u]);
            if (m != null) {
                for (int f = 0; f < m.objFieldOff.length; f++) {
                    if (m.objFieldOff[f] + idSize > nCount[u]) continue;
                    int val = b.getInt(nOff[u] + m.objFieldOff[f]);
                    if (val != 0 && id2idx.get(val) == v) return "." + m.objFieldName[f];
                }
            }
        } else if (nKind[u] == 1) {
            for (int e = 0; e < nCount[u]; e++) {
                int val = b.getInt(nOff[u] + e * idSize);
                if (val != 0 && id2idx.get(val) == v) return "[" + e + "]";
            }
        } else if (nKind[u] == 3) {
            ClassMeta m = classes.get(nClass[u]);
            if (m != null) for (int k = 0; k < m.staticObjVal.length; k++)
                if (id2idx.get(m.staticObjVal[k]) == v) return ".<static>" + m.staticObjName[k];
        }
        return "->";
    }

    static void printPath(int target) {
        ArrayList<Integer> chain = new ArrayList<>();
        int cur = target;
        int guard = 0;
        while (cur != -1 && cur != -2 && guard++ < 400) { chain.add(cur); if (cur == SUPER) break; cur = pred[cur]; }
        Collections.reverse(chain);
        for (int i = 0; i < chain.size(); i++) {
            int u = chain.get(i);
            String lbl = (i + 1 < chain.size()) ? edgeLabel(u, chain.get(i + 1)) : "";
            String nm = (u == SUPER) ? "GC ROOT" : nodeName(u);
            System.out.printf("    %s%s %s   [ret=%s]%n", "  ".repeat(Math.min(i, 30)), nm, lbl,
                    u == SUPER ? "-" : hs(retained[u]));
        }
    }

    // ---------------- reporting ----------------
    static String hs(long v) {
        if (v > 1024L * 1024 * 1024) return String.format("%.2f GB", v / 1024.0 / 1024 / 1024);
        if (v > 1024 * 1024) return String.format("%.1f MB", v / 1024.0 / 1024);
        if (v > 1024) return String.format("%.1f KB", v / 1024.0);
        return v + " B";
    }

    static void report(String[] args) {
        System.out.println("=== HEAPS ===");
        long[] heapBytes = new long[256]; int[] heapCnt = new int[256];
        for (int i = 0; i < N; i++) { if (nKind[i] == 3) continue; heapBytes[nHeap[i] & 0xff] += nSize[i]; heapCnt[nHeap[i] & 0xff]++; }
        for (int h = 0; h < 256; h++) if (heapCnt[h] > 0)
            System.out.printf("  heap %d (%s): %,d objects, %s%n", h, heapNames[h], heapCnt[h], hs(heapBytes[h]));

        // class histogram (shallow) over all nodes
        HashMap<String, long[]> hist = new HashMap<>(); // name -> {count, shallow, retained}
        for (int i = 0; i < N; i++) {
            String nm = nKind[i] == 3 ? "java.lang.Class" : (nKind[i] == 2 ? primName(nClass[i]) + "[]" : (nKind[i] == 1 ? cname(nClass[i]) : cname(nClass[i])));
            long[] a = hist.computeIfAbsent(nm, k -> new long[3]);
            a[0]++; a[1] += nSize[i]; a[2] += retained[i];
        }
        List<Map.Entry<String, long[]>> l = new ArrayList<>(hist.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1]));
        System.out.println("\n=== TOP 45 CLASSES BY SHALLOW SIZE ===");
        for (int i = 0; i < Math.min(45, l.size()); i++) {
            long[] a = l.get(i).getValue();
            System.out.printf("  %10s  %,10d  ret=%10s  %s%n", hs(a[1]), a[0], hs(a[2]), l.get(i).getKey());
        }
        l.sort((x, y) -> Long.compare(y.getValue()[2], x.getValue()[2]));
        System.out.println("\n=== TOP 45 CLASSES BY RETAINED SIZE (dominator) ===");
        for (int i = 0; i < Math.min(45, l.size()); i++) {
            long[] a = l.get(i).getValue();
            System.out.printf("  %10s  %,10d  shallow=%10s  %s%n", hs(a[2]), a[0], hs(a[1]), l.get(i).getKey());
        }
        l.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        System.out.println("\n=== TOP 40 CLASSES BY INSTANCE COUNT ===");
        for (int i = 0; i < Math.min(40, l.size()); i++) {
            long[] a = l.get(i).getValue();
            System.out.printf("  %,10d  %10s  ret=%10s  %s%n", a[0], hs(a[1]), hs(a[2]), l.get(i).getKey());
        }

        Integer[] idxs = new Integer[N];
        for (int i = 0; i < N; i++) idxs[i] = i;
        Arrays.sort(idxs, (x, y) -> Long.compare(retained[y], retained[x]));
        System.out.println("\n=== TOP 60 OBJECTS BY RETAINED SIZE ===");
        for (int k = 0; k < 60; k++) {
            int i = idxs[k];
            System.out.printf("  %10s  shallow=%9s  heap=%s  %s  @0x%x%n", hs(retained[i]), hs(nSize[i]),
                    heapNames[nHeap[i] & 0xff], nodeName(i), nIdList[i]);
        }

        System.out.println("\n=== SUSPECT CLASS COUNTS (Activity/Fragment/Bitmap/Cell) ===");
        String[] pats = {"Activity", "Fragment", "Bitmap", "ImageReceiver", "Cell", "Drawable", "Handler", "Runnable"};
        HashMap<String, long[]> susp = new HashMap<>();
        for (int i = 0; i < N; i++) {
            if (nKind[i] != 0) continue;
            String nm = cname(nClass[i]);
            for (String p : pats) if (nm.contains(p)) { long[] a = susp.computeIfAbsent(nm, k -> new long[3]); a[0]++; a[1] += nSize[i]; a[2] += retained[i]; break; }
        }
        List<Map.Entry<String, long[]>> sl = new ArrayList<>(susp.entrySet());
        sl.sort((x, y) -> Long.compare(y.getValue()[2], x.getValue()[2]));
        for (int i = 0; i < Math.min(50, sl.size()); i++) {
            long[] a = sl.get(i).getValue();
            System.out.printf("  %,7d  ret=%10s  shallow=%9s  %s%n", a[0], hs(a[2]), hs(a[1]), sl.get(i).getKey());
        }
    }
}