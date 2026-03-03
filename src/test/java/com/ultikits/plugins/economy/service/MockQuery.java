package com.ultikits.plugins.economy.service;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.interfaces.Query;

import java.util.Collection;
import java.util.List;

/**
 * Minimal Query stub for unit tests — returns a fixed list for all terminal operations.
 */
class MockQuery<T extends BaseDataEntity<String>> implements Query<T> {

    private final List<T> results;

    MockQuery(List<T> results) {
        this.results = results;
    }

    @Override public Query<T> where(String column)        { return this; }
    @Override public Query<T> and(String column)          { return this; }
    @Override public Query<T> eq(Object value)            { return this; }
    @Override public Query<T> ne(Object value)            { return this; }
    @Override public Query<T> gt(Object value)            { return this; }
    @Override public Query<T> lt(Object value)            { return this; }
    @Override public Query<T> gte(Object value)           { return this; }
    @Override public Query<T> lte(Object value)           { return this; }
    @Override public Query<T> like(String pattern)        { return this; }
    @Override public Query<T> in(Collection<?> values)    { return this; }
    @Override public Query<T> orderBy(String column)      { return this; }
    @Override public Query<T> orderByDesc(String column)  { return this; }
    @Override public Query<T> limit(int count)            { return this; }
    @Override public Query<T> offset(int start)           { return this; }

    @Override public List<T> list()    { return results; }
    @Override public T first()         { return results.isEmpty() ? null : results.get(0); }
    @Override public boolean exists()  { return !results.isEmpty(); }
    @Override public long count()      { return results.size(); }
    @Override public int delete()      { return results.size(); }
}
