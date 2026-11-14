package com.neusoft.health.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

/** SQL 只由服务端编写；所有用户值必须使用 #{p.xxx} 绑定，不能拼接。 */
@Mapper
public interface SqlMapper {
  class Provider {
    public static String sql(Map<String, Object> input) {
      return (String) input.get("sql");
    }
  }

  @SelectProvider(type = Provider.class, method = "sql")
  List<Map<String, Object>> query(@Param("sql") String sql, @Param("p") Map<String, Object> p);

  @UpdateProvider(type = Provider.class, method = "sql")
  int execute(@Param("sql") String sql, @Param("p") Map<String, Object> p);

  @InsertProvider(type = Provider.class, method = "sql")
  @Options(useGeneratedKeys = true, keyProperty = "p.id")
  int insert(@Param("sql") String sql, @Param("p") Map<String, Object> p);
}
