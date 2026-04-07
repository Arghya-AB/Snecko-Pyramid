import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.tagkit.*;
import soot.toolkits.graph.*;

// we need to handle arrays, array lists and just liveness in general
// assuming PTG is fine
class AllocObject {
    private final int lineNumber;
    private final String originMethodSignature;
    private final boolean isPseudo;
    private final Set<String> callSites;

    AllocObject(int lineNumber, SootMethod originMethod) {
        this(lineNumber, originMethod != null ? originMethod.getSignature() : "UNIVERSE", false);
    }

    AllocObject(int lineNumber, SootMethod originMethod, boolean isPseudo) {
        this(lineNumber, originMethod != null ? originMethod.getSignature() : "UNIVERSE", isPseudo);
    }

    AllocObject(int lineNumber, String originMethodSignature, boolean isPseudo) {
        this.lineNumber = lineNumber;
        this.originMethodSignature = originMethodSignature;
        this.isPseudo = isPseudo;
        this.callSites = new HashSet<>();
    }

    boolean isPseudo() {
        return isPseudo;
    }

    int getLineNumber() {
        return lineNumber;
    }

    String getOriginMethodSignature() {
        return originMethodSignature;
    }

    Set<String> getCallSites() {
        return callSites;
    }

    void addCallSite(int lineNumber) {
        callSites.add(Integer.toString(lineNumber));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AllocObject that = (AllocObject) o;
        return lineNumber == that.lineNumber && Objects.equals(originMethodSignature, that.originMethodSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNumber, originMethodSignature);
    }

    @Override
    public String toString() {
        return (isPseudo ? "P" : "O") + lineNumber + "@" + originMethodSignature;
    }
}

class ObjectField {
    private final AllocObject base;
    private final SootField field;

    ObjectField(AllocObject base, SootField field) {
        this.base = Objects.requireNonNull(base);
        this.field = Objects.requireNonNull(field);
    }

    AllocObject getBase() {
        return base;
    }

    SootField getField() {
        return field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ObjectField that = (ObjectField) o;
        return base.equals(that.base) && field.equals(that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, field);
    }

    @Override
    public String toString() {
        return base + "." + field.getName();
    }
}

class UnitState {
    final HashMap<Local, Set<AllocObject>> stack;
    final HashMap<ObjectField, Set<AllocObject>> heap;

    UnitState() {
        this.stack = new HashMap<>();
        this.heap = new HashMap<>();
    }

    UnitState(UnitState other) {
        this.stack = new HashMap<>();
        for (Map.Entry<Local, Set<AllocObject>> e : other.stack.entrySet()) {
            this.stack.put(e.getKey(), SampleSceneTransform.normalize(new HashSet<>(e.getValue())));
        }
        this.heap = new HashMap<>();
        for (Map.Entry<ObjectField, Set<AllocObject>> e : other.heap.entrySet()) {
            this.heap.put(e.getKey(), SampleSceneTransform.normalize(new HashSet<>(e.getValue())));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UnitState that = (UnitState) o;
        return stack.equals(that.stack) && heap.equals(that.heap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack, heap);
    }

    @Override
    public String toString() {
        return "Stack=" + stack + " Heap=" + heap;
    }
}

class MethodResult {
    final HashMap<ObjectField, Set<AllocObject>> heap;
    final Set<AllocObject> returned;

    MethodResult(HashMap<ObjectField, Set<AllocObject>> heap, Set<AllocObject> returned) {
        this.heap = heap;
        this.returned = returned;
    }
}

class InOutState {
    final Unit unit;
    final UnitState in;
    final UnitState out;
    Set<AllocObject> returned;

    InOutState(Unit unit) {
        this.unit = unit;
        this.in = new UnitState();
        this.out = new UnitState();
        this.returned = new HashSet<>();
    }
}

public class SampleSceneTransform extends SceneTransformer {
    public static final AllocObject UNIVERSE = new AllocObject(-2, "UNIVERSE", true) {
        @Override
        public String toString() {
            return "U";
        }
    };

    private static final Set<AllocObject> allObjects = new HashSet<>();
    private static final Map<String, AllocObject> canonicalObjects = new HashMap<>();
    private static final Map<AllocObject, Set<String>> liveFields = new HashMap<>();

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        allObjects.clear();
        canonicalObjects.clear();
        liveFields.clear();

        SootClass mainClass = Scene.v().getMainClass();
        SootMethod mainMethod = mainClass.getMethodByName("main");

        analyzeMethod(mainMethod, new HashMap<>(), new ArrayList<>(), new HashMap<>(), 0);

        /*
         * System.out.println("Points-to analysis completed.");
         * for (AllocObject obj : allObjects) {
         * if (!obj.isPseudo()) {
         * System.out.println("Allocated object: " + obj);
         * }
         * }
         */
        System.out.println("Liveness analysis:");
        for (Map.Entry<AllocObject, Set<String>> entry : liveFields.entrySet()) {
            System.out.println("Object " + entry.getKey() + " has live fields: " + entry.getValue());
        }
    }

    public static Set<AllocObject> normalize(Set<AllocObject> set) {
        if (set != null && set.contains(UNIVERSE)) {
            return new HashSet<>(Collections.singletonList(UNIVERSE));
        }
        return set == null ? new HashSet<>() : set;
    }

    private MethodResult analyzeMethod(SootMethod method, HashMap<ObjectField, Set<AllocObject>> initialHeap,
            List<Set<AllocObject>> argValues, Map<SootMethod, Integer> callCounts, int totalDepth) {
        int count = callCounts.getOrDefault(method, 0);
        if (count >= 5 || totalDepth >= 20) {
            Set<AllocObject> allArgs = new HashSet<>();
            for (Set<AllocObject> arg : argValues) {
                allArgs.addAll(arg);
            }
            UnitState temp = new UnitState();
            temp.heap.putAll(initialHeap);
            Set<AllocObject> reachable = reachableFrom(allArgs, temp);
            HashMap<ObjectField, Set<AllocObject>> newHeap = new HashMap<>(initialHeap);
            for (Map.Entry<ObjectField, Set<AllocObject>> entry : newHeap.entrySet()) {
                if (reachable.contains(entry.getKey().getBase())) {
                    entry.setValue(new HashSet<>(Collections.singletonList(UNIVERSE)));
                }
            }
            return new MethodResult(newHeap, new HashSet<>(Collections.singletonList(UNIVERSE)));
        }

        callCounts.put(method, count + 1);

        Body body;
        try {
            body = method.retrieveActiveBody();
        } catch (RuntimeException e) {
            return new MethodResult(new HashMap<>(), new HashSet<>());
        }
        if (body == null)
            return new MethodResult(new HashMap<>(), new HashSet<>());
        UnitGraph cfg = new BriefUnitGraph(body);

        Map<Unit, Set<AllocObject>> paramBindings = new HashMap<>();
        List<Local> paramLocals = new ArrayList<>();
        int idx = 0;
        for (Unit u : body.getUnits()) {
            if (u instanceof IdentityStmt && idx < argValues.size()) {
                Value left = ((IdentityStmt) u).getLeftOp();
                if (left instanceof Local) {
                    paramBindings.put(u, argValues.get(idx));
                    paramLocals.add((Local) left);
                }
                idx++;
            }
        }

        Map<Unit, InOutState> stateMap = new HashMap<>();
        for (Unit u : body.getUnits()) {
            InOutState ios = new InOutState(u);
            for (Map.Entry<ObjectField, Set<AllocObject>> e : initialHeap.entrySet()) {
                ios.in.heap.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            stateMap.put(u, ios);
        }

        LinkedList<Unit> worklist = new LinkedList<>(body.getUnits());
        while (!worklist.isEmpty()) {
            Unit u = worklist.removeFirst();
            InOutState ios = stateMap.get(u);

            UnitState newIn = new UnitState();
            for (Map.Entry<ObjectField, Set<AllocObject>> e : initialHeap.entrySet()) {
                newIn.heap.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            for (Unit pred : cfg.getPredsOf(u)) {
                UnitState predOut = stateMap.get(pred).out;
                newIn = join(newIn, predOut);
            }
            copyState(newIn, ios.in);

            Set<AllocObject> returned = processStatement(u, ios.in, ios.out, paramBindings, method, callCounts,
                    totalDepth);
            ios.returned = returned == null ? new HashSet<>() : returned;

            for (Unit succ : cfg.getSuccsOf(u)) {
                if (!ios.out.equals(stateMap.get(succ).in)) {
                    worklist.add(succ);
                }
            }
        }

        /*
         * System.out.println("\n--- PTG for method: " + method.getSignature() +
         * " ---");
         * for (Unit u : body.getUnits()) {
         * InOutState ios = stateMap.get(u);
         * System.out.println("Stmt [" + getLineNumber(u) + "]: " + u);
         * System.out.println("  => PTG: " + ios.out);
         * }
         */

        HashMap<ObjectField, Set<AllocObject>> mergedHeap = new HashMap<>();
        Set<AllocObject> mergedReturned = new HashSet<>();

        for (InOutState ios : stateMap.values()) {
            Unit u = ios.unit;
            boolean isExit = (u instanceof ReturnStmt || u instanceof ReturnVoidStmt) || cfg.getSuccsOf(u).isEmpty();
            if (isExit) {
                for (Map.Entry<ObjectField, Set<AllocObject>> e : ios.out.heap.entrySet()) {
                    mergedHeap.compute(e.getKey(), (k, v) -> {
                        if (v == null)
                            return normalize(new HashSet<>(e.getValue()));
                        v.addAll(e.getValue());
                        return normalize(v);
                    });
                }
                mergedReturned.addAll(ios.returned);
            }
        }
        return new MethodResult(mergedHeap, mergedReturned);
    }

    private void copyState(UnitState src, UnitState dst) {
        dst.stack.clear();
        for (Map.Entry<Local, Set<AllocObject>> e : src.stack.entrySet()) {
            dst.stack.put(e.getKey(), normalize(new HashSet<>(e.getValue())));
        }
        dst.heap.clear();
        for (Map.Entry<ObjectField, Set<AllocObject>> e : src.heap.entrySet()) {
            dst.heap.put(e.getKey(), new HashSet<>(e.getValue()));
        }
    }

    private int getLineNumber(Unit u) {
        if (u instanceof Stmt) {
            Stmt s = (Stmt) u;
            Tag tag = s.getTag("LineNumberTag");
            if (tag instanceof LineNumberTag)
                return ((LineNumberTag) tag).getLineNumber();
            Tag sourceTag = s.getTag("SourceLineNumberTag");
            if (sourceTag instanceof soot.tagkit.SourceLineNumberTag)
                return ((soot.tagkit.SourceLineNumberTag) sourceTag).getLineNumber();
        }
        return -1;
    }

    private Set<AllocObject> processStatement(Unit stmt, UnitState in, UnitState out,
            Map<Unit, Set<AllocObject>> paramBindings, SootMethod method, Map<SootMethod, Integer> callCounts,
            int totalDepth) {
        copyState(in, out);

        if (stmt instanceof IdentityStmt) {
            Set<AllocObject> binding = paramBindings.get(stmt);
            if (binding != null) {
                Value left = ((IdentityStmt) stmt).getLeftOp();
                if (left instanceof Local) {
                    out.stack.put((Local) left, normalize(new HashSet<>(binding)));
                }
            }
            return new HashSet<>();
        }

        if (stmt instanceof ReturnStmt)
            return handleReturn((ReturnStmt) stmt, out, method);
        if (stmt instanceof ReturnVoidStmt)
            return new HashSet<>();

        if (stmt instanceof InvokeStmt) {
            handleInvoke(((InvokeStmt) stmt).getInvokeExpr(), stmt, in, out, method, callCounts, totalDepth);
            return new HashSet<>();
        }

        if (stmt instanceof AssignStmt) {
            handleAssign((AssignStmt) stmt, in, out, method, callCounts, totalDepth);
        }
        return new HashSet<>();
    }

    private void handleAssign(AssignStmt assign, UnitState in, UnitState out, SootMethod method,
            Map<SootMethod, Integer> callCounts, int totalDepth) {
        Value left = assign.getLeftOp();
        Value right = assign.getRightOp();
        int line = getLineNumber(assign);

        if (right instanceof InvokeExpr) {
            Set<AllocObject> ret = handleInvoke((InvokeExpr) right, assign, in, out, method, callCounts, totalDepth);
            if (left instanceof Local) {
                if (ret.isEmpty())
                    out.stack.remove((Local) left);
                else
                    out.stack.put((Local) left, normalize(new HashSet<>(ret)));
            } else if (left instanceof InstanceFieldRef) {
                InstanceFieldRef ifr = (InstanceFieldRef) left;
                Value base = ifr.getBase();
                SootField field = ifr.getField();
                if (base instanceof Local) {
                    Set<AllocObject> bases = out.stack.get((Local) base);
                    if (bases != null) {
                        for (AllocObject o : bases) {
                            if (o != UNIVERSE) {
                                liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                            }
                        }
                        for (AllocObject o : bases) {
                            if (o == UNIVERSE)
                                continue;
                            ObjectField key = new ObjectField(o, field);
                            Set<AllocObject> cur = out.heap.get(key);
                            if (cur == null) {
                                cur = new HashSet<>();
                            }
                            cur.addAll(ret);
                            out.heap.put(key, normalize(cur));
                        }
                    }
                }
            }
            return;
        }

        if (left instanceof StaticFieldRef)
            return;
        if (right instanceof StaticFieldRef && left instanceof Local) {
            out.stack.put((Local) left, normalize(normalize(new HashSet<>(Collections.singletonList(UNIVERSE)))));
            return;
        }
        if (right instanceof InstanceFieldRef && ((InstanceFieldRef) right).getBase() instanceof StaticFieldRef
                && left instanceof Local) {
            out.stack.put((Local) left, normalize(normalize(new HashSet<>(Collections.singletonList(UNIVERSE)))));
            return;
        }

        if (right instanceof NewExpr) {
            handleNewOrConst(left, right, line, out, method, false);
            return;
        }

        if (left instanceof Local && right instanceof Expr && !(right instanceof NewExpr)) {
            handleNewOrConst(left, right, line, out, method, true);
            return;
        }

        if (right instanceof Constant) {
            handleNewOrConst(left, right, line, out, method, true);
            return;
        }

        if (right instanceof Local && left instanceof Local) {
            handleCopy((Local) left, (Local) right, out);
            return;
        }

        if (left instanceof InstanceFieldRef && right instanceof Local) {
            handleFieldStore((InstanceFieldRef) left, (Local) right, out);
            return;
        }

        if (left instanceof Local && right instanceof InstanceFieldRef) {
            handleFieldLoad((Local) left, (InstanceFieldRef) right, out);
            return;
        }
    }

    private void handleNewOrConst(Value left, Value right, int line, UnitState out, SootMethod method,
            boolean isPseudo) {
        String allocSuffix = isPseudo ? " [" + right.toString() + "]" : "";
        String allocKey = (isPseudo ? "P" : "O") + line + "@" + method.getSignature() + allocSuffix;
        AllocObject obj = canonicalObjects.get(allocKey);
        if (obj == null) {
            obj = new AllocObject(line, method.getSignature() + allocSuffix, isPseudo);
            canonicalObjects.put(allocKey, obj);
            allObjects.add(obj);
        }
        if (left instanceof Local) {
            Set<AllocObject> set = new HashSet<>();
            set.add(obj);
            out.stack.put((Local) left, normalize(set));
        } else if (left instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) left;
            Value base = ifr.getBase();
            SootField field = ifr.getField();
            Set<AllocObject> bases = resolveValuePoints(base, out);
            if (!bases.isEmpty()) {
                for (AllocObject o : bases) {
                    if (o != UNIVERSE) {
                        liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                    }
                }
            }
            if (!bases.isEmpty()) {
                boolean canStrongUpdate = bases.size() == 1 && !bases.contains(UNIVERSE);
                final AllocObject canonical = obj;
                for (AllocObject o : bases) {
                    if (o == UNIVERSE)
                        continue;
                    ObjectField key = new ObjectField(o, field);
                    if (canStrongUpdate) {
                        out.heap.put(key, normalize(new HashSet<>(Set.of(canonical))));
                    } else {
                        out.heap.compute(key, (k, v) -> {
                            if (v == null)
                                return normalize(new HashSet<>(Set.of(canonical)));
                            v.add(canonical);
                            return normalize(v);
                        });
                    }
                }
            }
        }
    }

    private void handleCopy(Local left, Local right, UnitState out) {
        Set<AllocObject> rval = out.stack.get(right);
        if (rval != null)
            out.stack.put(left, normalize(new HashSet<>(rval)));
        else
            out.stack.remove(left);
    }

    private void handleFieldStore(InstanceFieldRef leftRef, Local right, UnitState out) {
        Value base = leftRef.getBase();
        SootField field = leftRef.getField();
        Set<AllocObject> bset = out.stack.get(right);
        Set<AllocObject> bases = resolveValuePoints(base, out);
        if (!bases.isEmpty()) {
            for (AllocObject o : bases) {
                if (o != UNIVERSE) {
                    liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                }
            }
        }
        if (!bases.isEmpty() && bset != null) {
            boolean canStrongUpdate = bases.size() == 1 && !bases.contains(UNIVERSE);
            for (AllocObject o : bases) {
                if (o == UNIVERSE)
                    continue;
                ObjectField key = new ObjectField(o, field);
                if (canStrongUpdate) {
                    out.heap.put(key, normalize(new HashSet<>(bset)));
                } else {
                    Set<AllocObject> cur = out.heap.get(key);
                    if (cur == null) {
                        cur = new HashSet<>();
                        out.heap.put(key, cur);
                    }
                    cur.addAll(bset);
                    out.heap.put(key, normalize(cur));
                }
            }
        }
    }

    private void handleFieldLoad(Local left, InstanceFieldRef rightRef, UnitState out) {
        Value base = rightRef.getBase();
        SootField field = rightRef.getField();
        Set<AllocObject> bases = out.stack.get((Local) base);
        if (bases != null) {
            for (AllocObject o : bases) {
                if (o != UNIVERSE) {
                    liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                }
            }
        }
        Set<AllocObject> resultSet = new HashSet<>();
        if (bases != null) {
            if (bases.contains(UNIVERSE)) {
                resultSet.add(UNIVERSE);
            }
            for (AllocObject o : bases) {
                if (o == UNIVERSE)
                    continue;
                ObjectField key = new ObjectField(o, field);
                Set<AllocObject> rset = out.heap.get(key);
                if (rset != null)
                    resultSet.addAll(rset);
            }
        }
        if (!resultSet.isEmpty())
            out.stack.put(left, normalize(resultSet));
        else
            out.stack.remove(left);
    }

    private Set<AllocObject> reachableFrom(Set<AllocObject> seeds, UnitState state) {
        Set<AllocObject> visited = new HashSet<>(seeds);
        LinkedList<AllocObject> work = new LinkedList<>(seeds);
        while (!work.isEmpty()) {
            AllocObject o = work.removeFirst();
            for (Map.Entry<ObjectField, Set<AllocObject>> e : state.heap.entrySet()) {
                if (e.getKey().getBase().equals(o)) {
                    for (AllocObject tgt : e.getValue()) {
                        if (visited.add(tgt)) {
                            work.add(tgt);
                        }
                    }
                }
            }
        }
        return visited;
    }

    private Set<AllocObject> handleReturn(ReturnStmt ret, UnitState out, SootMethod method) {
        Value val = ret.getOp();
        Set<AllocObject> returned = new HashSet<>();
        if (val instanceof Constant) {
            int line = getLineNumber(ret);
            String allocSuffix = " [" + val.toString() + "]";
            String allocKey = "P" + line + "@" + method.getSignature() + allocSuffix;
            AllocObject obj = canonicalObjects.get(allocKey);
            if (obj == null) {
                obj = new AllocObject(line, method.getSignature() + allocSuffix, true);
                canonicalObjects.put(allocKey, obj);
                allObjects.add(obj);
            }
            returned.add(obj);
        } else if (val instanceof Local) {
            Set<AllocObject> seeds = out.stack.get(val);
            if (seeds != null)
                returned.addAll(seeds);
        } else if (val instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) val;
            Value base = ifr.getBase();
            SootField field = ifr.getField();
            if (base instanceof Local) {
                Set<AllocObject> bases = out.stack.get((Local) base);
                if (bases != null) {
                    for (AllocObject o : bases) {
                        if (o != UNIVERSE) {
                            liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                        }
                    }
                    Set<AllocObject> seeds = new HashSet<>();
                    if (bases.contains(UNIVERSE)) {
                        seeds.add(UNIVERSE);
                    }
                    for (AllocObject o : bases) {
                        if (o == UNIVERSE)
                            continue;
                        ObjectField key = new ObjectField(o, field);
                        Set<AllocObject> rset = out.heap.get(key);
                        if (rset != null)
                            seeds.addAll(rset);
                    }
                    returned.addAll(seeds);
                }
            }
        }
        return returned;
    }

    private Set<AllocObject> resolveValuePoints(Value val, UnitState out) {
        if (val instanceof Local) {
            Set<AllocObject> pts = out.stack.get((Local) val);
            return pts == null ? new HashSet<>() : new HashSet<>(pts);
        }
        if (val instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) val;
            Value base = ifr.getBase();
            SootField field = ifr.getField();
            Set<AllocObject> result = new HashSet<>();
            if (base instanceof Local) {
                Set<AllocObject> bases = out.stack.get((Local) base);
                if (bases != null) {
                    for (AllocObject o : bases) {
                        if (o != UNIVERSE) {
                            liveFields.computeIfAbsent(o, k -> new HashSet<>()).add(field.getName());
                        }
                    }
                    if (bases.contains(UNIVERSE)) {
                        result.add(UNIVERSE);
                    }
                    for (AllocObject o : bases) {
                        if (o == UNIVERSE)
                            continue;
                        ObjectField key = new ObjectField(o, field);
                        Set<AllocObject> rset = out.heap.get(key);
                        if (rset != null)
                            result.addAll(rset);
                    }
                }
            }
            return normalize(result);
        }
        return new HashSet<>();
    }

    private Set<AllocObject> handleInvoke(InvokeExpr invoke, Unit site, UnitState in, UnitState out,
            SootMethod callerMethod, Map<SootMethod, Integer> callCounts, int totalDepth) {
        SootMethod declared = invoke.getMethod();
        if (declared.isJavaLibraryMethod() || declared.isPhantom()) {
            int line = getLineNumber(site);
            String allocSuffix = " [" + declared.getName() + "]";
            String allocKey = "P" + line + "@" + callerMethod.getSignature() + allocSuffix;
            AllocObject obj = canonicalObjects.get(allocKey);
            if (obj == null) {
                obj = new AllocObject(line, callerMethod.getSignature() + allocSuffix, true);
                canonicalObjects.put(allocKey, obj);
                allObjects.add(obj);
            }
            return new HashSet<>(Collections.singletonList(obj));
        }

        Set<SootMethod> targets = new HashSet<>();
        targets.add(declared);
        if (invoke instanceof InstanceInvokeExpr) {
            Type baseType = ((InstanceInvokeExpr) invoke).getBase().getType();
            if (baseType instanceof RefType) {
                SootClass baseClass = ((RefType) baseType).getSootClass();
                List<SootMethod> resolved = Scene.v().getActiveHierarchy()
                        .resolveAbstractDispatch(Collections.singletonList(baseClass), declared);
                targets.addAll(resolved);
            }
        }

        Set<AllocObject> retSet = new HashSet<>();
        HashMap<ObjectField, Set<AllocObject>> newHeap = new HashMap<>();
        boolean targetAnalyzed = false;

        for (SootMethod target : targets) {
            List<Set<AllocObject>> argValues = new ArrayList<>();
            if (invoke instanceof InstanceInvokeExpr) {
                Value base = ((InstanceInvokeExpr) invoke).getBase();
                argValues.add(resolveValuePoints(base, out));
            }
            for (Value arg : invoke.getArgs()) {
                if (arg instanceof Constant) {
                    int line = getLineNumber(site);
                    String allocSuffix = " [" + arg.toString() + "]";
                    String allocKey = "P" + line + "@" + callerMethod.getSignature() + allocSuffix;
                    AllocObject obj = canonicalObjects.get(allocKey);
                    if (obj == null) {
                        obj = new AllocObject(line, callerMethod.getSignature() + allocSuffix, true);
                        canonicalObjects.put(allocKey, obj);
                        allObjects.add(obj);
                    }
                    argValues.add(new HashSet<>(Collections.singletonList(obj)));
                } else {
                    argValues.add(resolveValuePoints(arg, out));
                }
            }

            Map<SootMethod, Integer> newCallCounts = new HashMap<>(callCounts);
            MethodResult result = analyzeMethod(target, out.heap, argValues, newCallCounts, totalDepth + 1);

            targetAnalyzed = true;

            for (Map.Entry<ObjectField, Set<AllocObject>> e : result.heap.entrySet()) {
                newHeap.compute(e.getKey(), (k, v) -> {
                    if (v == null)
                        return normalize(new HashSet<>(e.getValue()));
                    v.addAll(e.getValue());
                    return normalize(v);
                });
            }

            retSet.addAll(result.returned);
        }

        if (!targetAnalyzed) {
            for (Map.Entry<ObjectField, Set<AllocObject>> e : out.heap.entrySet()) {
                newHeap.put(e.getKey(), new HashSet<>(e.getValue()));
            }
        }

        out.heap.clear();
        for (Map.Entry<ObjectField, Set<AllocObject>> e : newHeap.entrySet()) {
            out.heap.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        return retSet;
    }

    private UnitState join(UnitState a, UnitState b) {
        UnitState result = new UnitState(a);
        for (Map.Entry<Local, Set<AllocObject>> e : b.stack.entrySet()) {
            result.stack.compute(e.getKey(), (k, v) -> {
                if (v == null)
                    return normalize(new HashSet<>(e.getValue()));
                v.addAll(e.getValue());
                return normalize(v);
            });
        }
        for (Map.Entry<ObjectField, Set<AllocObject>> e : b.heap.entrySet()) {
            result.heap.compute(e.getKey(), (k, v) -> {
                if (v == null)
                    return normalize(new HashSet<>(e.getValue()));
                v.addAll(e.getValue());
                return normalize(v);
            });
        }
        return result;
    }
}
