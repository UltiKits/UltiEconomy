package com.ultikits.plugins.economy.testsupport;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Test support: a stateful, in-memory {@link DataOperator} that behaves like the framework's
 * storage backends closely enough to test what the module leaves in storage, not which calls it
 * made.
 *
 * <p>Two shapes, chosen at construction:
 * <ul>
 *   <li>{@link #relational(String, Class, CrashSwitch)} -- like the SQLite and MySQL operators with
 *       no transaction open: every insert, update and delete is durable the moment it returns
 *       (auto-commit).</li>
 *   <li>{@link #cached(String, Class, CrashSwitch)} -- like the JSON backend
 *       ({@code SimpleJsonDataOperator}): writes change only an in-memory cache; {@link #flush()}
 *       writes every cached record to "disk" one record at a time and never removes one;
 *       {@link #gc()} removes the disk records no longer cached, also one at a time. What reaches disk is only what a
 *       flush wrote. Setting {@link #setEagerFlush(boolean)} flushes after every write, which
 *       models the framework's background flush firing at the worst moment.</li>
 * </ul>
 *
 * <p>Every read returns copies and every write stores copies, so a caller that mutates an entity
 * without writing it back changes nothing -- exactly as on a relational backend.
 *
 * <p>{@link CrashSwitch} counts durable write steps across every operator that shares it and throws
 * {@link SimulatedCrash} at the chosen step; {@link #restartFromDisk()} then drops whatever was not
 * durable, which is the state a server restarted after that crash would read.
 */
public final class InMemoryDataOperator<T extends BaseDataEntity<String>> implements DataOperator<T>, Cached {

    /** Thrown at the step a {@link CrashSwitch} was armed for; nothing after it happens. */
    public static final class SimulatedCrash extends RuntimeException {
        public SimulatedCrash(String step) {
            super("simulated crash at " + step);
        }
    }

    /** Counts durable write steps across operators and throws at the armed one (1-based; 0 = never). */
    public static final class CrashSwitch {
        private int count;
        private int crashAt;
        private final List<String> steps = new ArrayList<>();

        public void armAt(int step) {
            this.crashAt = step;
            this.count = 0;
            this.steps.clear();
        }

        public void disarm() {
            this.crashAt = 0;
        }

        public int count() {
            return count;
        }

        public List<String> steps() {
            return new ArrayList<>(steps);
        }

        void step(String what) {
            count++;
            if (crashAt > 0 && count == crashAt) {
                throw new SimulatedCrash(what + " (step " + count + ")");
            }
            steps.add(what);
        }
    }

    private final String name;
    private final Class<T> type;
    private final boolean cachedBackend;
    private final CrashSwitch crashSwitch;
    private final Map<String, T> disk = new LinkedHashMap<>();
    private Map<String, T> cache = new LinkedHashMap<>();
    private boolean eagerFlush;
    private int flushCount;

    private InMemoryDataOperator(String name, Class<T> type, boolean cachedBackend, CrashSwitch crashSwitch) {
        this.name = name;
        this.type = type;
        this.cachedBackend = cachedBackend;
        this.crashSwitch = crashSwitch != null ? crashSwitch : new CrashSwitch();
    }

    public static <T extends BaseDataEntity<String>> InMemoryDataOperator<T> relational(
            String name, Class<T> type, CrashSwitch crashSwitch) {
        return new InMemoryDataOperator<>(name, type, false, crashSwitch);
    }

    public static <T extends BaseDataEntity<String>> InMemoryDataOperator<T> cached(
            String name, Class<T> type, CrashSwitch crashSwitch) {
        return new InMemoryDataOperator<>(name, type, true, crashSwitch);
    }

    public void setEagerFlush(boolean eagerFlush) {
        this.eagerFlush = eagerFlush;
    }

    /** How many times {@link #flush()} ran to completion. */
    public int flushCount() {
        return flushCount;
    }

    /** Seeds a record directly into durable storage, as an earlier version would have left it. */
    public T seed(T entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
        }
        disk.put(entity.getId(), copy(entity));
        cache.put(entity.getId(), copy(entity));
        return entity;
    }

    /** What a server restarted now would read: the durable records only. */
    public void restartFromDisk() {
        cache = new LinkedHashMap<>();
        for (Map.Entry<String, T> e : disk.entrySet()) {
            cache.put(e.getKey(), copy(e.getValue()));
        }
    }

    /** Copies of the durable records. */
    public List<T> durable() {
        List<T> out = new ArrayList<>();
        for (T t : disk.values()) {
            out.add(copy(t));
        }
        return out;
    }

    // ----- DataOperator -----

    @Override
    public boolean exist(T object) {
        return object.getId() != null && cache.containsKey(object.getId());
    }

    @Override
    public boolean exist(WhereCondition... whereConditions) {
        return !getAll(whereConditions).isEmpty();
    }

    @Override
    public T getById(Object id) {
        T t = cache.get(String.valueOf(id));
        return t == null ? null : copy(t);
    }

    @Override
    public List<T> getAll() {
        List<T> out = new ArrayList<>();
        for (T t : cache.values()) {
            out.add(copy(t));
        }
        return out;
    }

    @Override
    public List<T> getAll(WhereCondition... whereConditions) {
        List<T> out = new ArrayList<>();
        for (T t : cache.values()) {
            if (matches(t, whereConditions)) {
                out.add(copy(t));
            }
        }
        return out;
    }

    @Override
    public List<T> getLike(String column, String value, LikeType likeType) {
        throw new UnsupportedOperationException("getLike is not used by this module");
    }

    @Override
    public List<T> page(int page, int size, WhereCondition... whereConditions) {
        throw new UnsupportedOperationException("page is not used by this module");
    }

    @Override
    public void insert(T obj) {
        if (obj.getId() == null) {
            obj.setId(UUID.randomUUID().toString());
        }
        if (cache.containsKey(obj.getId())) {
            // Measured against the framework (UltiTools-API 6.3.0-SNAPSHOT): on SQLite and MySQL the
            // table's PRIMARY KEY (id) refuses the INSERT, and AbstractRelationalDataOperator.insert
            // (line 516) rethrows the SQLException as this exception; the JSON operator's insert is
            // cache.putIfAbsent (SimpleJsonDataOperator line 392), which keeps the existing record.
            if (!cachedBackend) {
                throw new DataAccessException(ErrorCode.DATA_OPERATION_FAILED, "Failed to insert entity");
            }
            return;
        }
        write("insert " + name + " " + obj.getId());
        cache.put(obj.getId(), copy(obj));
        afterWrite();
    }

    @Override
    public void del(WhereCondition... whereConditions) {
        if (whereConditions == null || whereConditions.length == 0) {
            throw new IllegalArgumentException("refusing to delete every row");
        }
        for (T t : getAll(whereConditions)) {
            delById(t.getId());
        }
    }

    @Override
    public void delById(Object id) {
        write("delete " + name + " " + id);
        cache.remove(String.valueOf(id));
        if (!cachedBackend) {
            disk.remove(String.valueOf(id));
        }
        afterWrite();
    }

    @Override
    public void update(String column, Object value, Object id) {
        T existing = cache.get(String.valueOf(id));
        if (existing == null) {
            return;
        }
        T changed = copy(existing);
        try {
            field(column).set(changed, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        write("update " + name + " " + id + " " + column);
        cache.put(changed.getId(), changed);
        afterWrite();
    }

    @Override
    public void update(T obj) throws IllegalAccessException {
        if (obj.getId() == null || !cache.containsKey(obj.getId())) {
            // A relational UPDATE ... WHERE id = ? that matches nothing changes nothing.
            return;
        }
        write("update " + name + " " + obj.getId());
        cache.put(obj.getId(), copy(obj));
        afterWrite();
    }

    // ----- Cached -----

    @Override
    public void flush() {
        if (!cachedBackend) {
            return;
        }
        for (Map.Entry<String, T> e : new ArrayList<>(cache.entrySet())) {
            crashSwitch.step("flush " + name + " " + e.getKey());
            disk.put(e.getKey(), copy(e.getValue()));
        }
        flushCount++;
    }

    @Override
    public void gc() {
        if (!cachedBackend) {
            return;
        }
        // Like the real gc(), one file at a time, so a crash can fall between two deletions.
        for (String key : new ArrayList<>(disk.keySet())) {
            if (!cache.containsKey(key)) {
                crashSwitch.step("gc " + name + " " + key);
                disk.remove(key);
            }
        }
    }

    // ----- internals -----

    private void write(String what) {
        if (!cachedBackend) {
            crashSwitch.step(what);
        }
    }

    private void afterWrite() {
        if (!cachedBackend) {
            // durable on return, like an auto-committed statement
            disk.clear();
            for (Map.Entry<String, T> e : cache.entrySet()) {
                disk.put(e.getKey(), copy(e.getValue()));
            }
        } else if (eagerFlush) {
            flush();
        }
    }

    private boolean matches(T t, WhereCondition... conditions) {
        if (conditions == null) {
            return true;
        }
        for (WhereCondition c : conditions) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            try {
                Object v = field(c.getColumn()).get(t);
                if (!Objects.equals(v == null ? null : String.valueOf(v),
                        c.getValue() == null ? null : String.valueOf(c.getValue()))) {
                    return false;
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return true;
    }

    private Field field(String column) {
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                Column col = f.getAnnotation(Column.class);
                if ((col != null && col.value().equals(column)) || f.getName().equals(column)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new IllegalArgumentException("no column " + column + " on " + type.getSimpleName());
    }

    private T copy(T source) {
        try {
            T target = type.getDeclaredConstructor().newInstance();
            for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    f.set(target, f.get(source));
                }
            }
            return target;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot copy " + type.getSimpleName(), e);
        }
    }
}
