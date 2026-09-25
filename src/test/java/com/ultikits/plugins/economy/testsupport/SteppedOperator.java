package com.ultikits.plugins.economy.testsupport;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.util.List;
import java.util.function.Consumer;

/**
 * Test support: one server's view of a shared {@link DataOperator}. Every call is announced to
 * {@code beforeEachCall} (for example {@link Lockstep#stepper(String)}, which waits for the server's
 * turn) and then passed to the shared operator unchanged.
 */
public final class SteppedOperator<T extends BaseDataEntity<String>> implements DataOperator<T>, Cached {

    private final String table;
    private final DataOperator<T> shared;
    private final Consumer<String> beforeEachCall;

    public SteppedOperator(String table, DataOperator<T> shared, Consumer<String> beforeEachCall) {
        this.table = table;
        this.shared = shared;
        this.beforeEachCall = beforeEachCall;
    }

    private void announce(String call) {
        beforeEachCall.accept(call + " " + table);
    }

    @Override
    public boolean exist(T object) {
        announce("exist");
        return shared.exist(object);
    }

    @Override
    public boolean exist(WhereCondition... whereConditions) {
        announce("exist");
        return shared.exist(whereConditions);
    }

    @Override
    public T getById(Object id) {
        announce("getById");
        return shared.getById(id);
    }

    @Override
    public List<T> getAll() {
        announce("getAll");
        return shared.getAll();
    }

    @Override
    public List<T> getAll(WhereCondition... whereConditions) {
        announce("getAll(where)");
        return shared.getAll(whereConditions);
    }

    @Override
    public List<T> getLike(String column, String value, LikeType likeType) {
        announce("getLike");
        return shared.getLike(column, value, likeType);
    }

    @Override
    public List<T> page(int page, int size, WhereCondition... whereConditions) {
        announce("page");
        return shared.page(page, size, whereConditions);
    }

    @Override
    public void insert(T obj) {
        announce("insert");
        shared.insert(obj);
    }

    @Override
    public void del(WhereCondition... whereConditions) {
        announce("del(where)");
        shared.del(whereConditions);
    }

    @Override
    public void delById(Object id) {
        announce("delById");
        shared.delById(id);
    }

    @Override
    public void update(String column, Object value, Object id) {
        announce("update(" + column + ")");
        shared.update(column, value, id);
    }

    @Override
    public void update(T obj) throws IllegalAccessException {
        announce("update");
        shared.update(obj);
    }

    @Override
    public void flush() {
        if (shared instanceof Cached) {
            announce("flush");
            ((Cached) shared).flush();
        }
    }

    @Override
    public void gc() {
        if (shared instanceof Cached) {
            announce("gc");
            ((Cached) shared).gc();
        }
    }
}
