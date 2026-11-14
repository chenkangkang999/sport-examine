package com.neusoft.health.mapper;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class Database {
  private final SqlMapper mapper;

  public Database(SqlMapper mapper) {
    this.mapper = mapper;
  }

  public static Map<String, Object> params(Object... pairs) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i].toString(), pairs[i + 1]);
    return m;
  }

  public List<Map<String, Object>> rows(String sql, Map<String, Object> p) {
    return mapper.query(sql, p);
  }

  public List<Map<String, Object>> rows(String sql, Object... p) {
    return rows(sql, params(p));
  }

  public Map<String, Object> one(String sql, Map<String, Object> p) {
    var r = rows(sql, p);
    return r.isEmpty() ? null : r.get(0);
  }

  public Map<String, Object> one(String sql, Object... p) {
    return one(sql, params(p));
  }

  public int exec(String sql, Map<String, Object> p) {
    return mapper.execute(sql, p);
  }

  public int exec(String sql, Object... p) {
    return exec(sql, params(p));
  }

  public long insert(String sql, Map<String, Object> p) {
    var v = new HashMap<>(p);
    mapper.insert(sql, v);
    return ((Number) v.get("id")).longValue();
  }

  public long insert(String sql, Object... p) {
    return insert(sql, params(p));
  }
}
