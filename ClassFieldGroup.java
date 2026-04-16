import java.util.*;
import soot.*;

public class ClassFieldGroup {
    private final SootClass sc;
    private final Set<SootField> fields;

    public ClassFieldGroup(SootClass sc, Set<SootField> fields) {
        this.sc = sc;
        Set<SootField> liveFields = new HashSet<>(fields);
        Set<SootField> allFields = getAllFields(sc);
        // only keeping liveFields which are part of allFields i.e. intersection
        liveFields.retainAll(allFields);
        this.fields = liveFields;
    }

    public static Set<SootField> getAllFields(SootClass sClass) {
        Set<SootField> allFields = new HashSet<>();
        SootClass currentClass = sClass;

        while (currentClass != null) {
            allFields.addAll(currentClass.getFields());
            
            // Check if there is a superclass and if it's not java.lang.Object (optional stop)
            if (currentClass.hasSuperclass()) {
                currentClass = currentClass.getSuperclass();
            } else {
                currentClass = null;
            }
        }
        return allFields;
    }

    public SootClass getSootClass() {
        return sc;
    }

    public Set<SootField> getFields() {
        return fields;
    }

    // Equals implementation
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassFieldGroup that = (ClassFieldGroup) o;
        return Objects.equals(sc, that.sc) && 
               Objects.equals(fields, that.fields);
    }

    // HashCode implementation
    @Override
    public int hashCode() {
        return Objects.hash(sc, fields);
    }

    @Override
    public String toString() {
        return "ClassFieldGroup{" +
                "class=" + (sc != null ? sc.getName() : "null") +
                ", fieldsCount=" + (fields != null ? fields.size() : 0) +
                '}';
    }

}
