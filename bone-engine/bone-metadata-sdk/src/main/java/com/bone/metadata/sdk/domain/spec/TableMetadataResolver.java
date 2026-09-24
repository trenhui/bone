package com.bone.metadata.sdk.domain.spec;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.SequenceGenerator;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import com.bone.metadata.sdk.domain.exception.MetadataException;
import com.bone.metadata.sdk.domain.model.CascadeRelation;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.support.util.SqlUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/** 元数据解析器，支持缓存。 */
@Slf4j
public final class TableMetadataResolver {

  // 使用Caffeine实现的线程安全缓存
  private static final Cache<String, TableMetadata> METADATA_CACHE =
      Caffeine.newBuilder()
          .maximumSize(2000) // 最大缓存容量
          .expireAfterAccess(100, TimeUnit.HOURS) // 缓存过期时间
          .build();

  /**
   * 获取表元数据，如果缓存中有，则直接返回，否则解析并缓存。
   *
   * @param entityClass 实体类类型
   * @return TableMetadata
   */
  public static TableMetadata load(Class<?> entityClass) {
    return METADATA_CACHE.get(entityClass.getName(), key -> parseTableMetadata(entityClass));
  }

  /**
   * 解析 TableMetadata 核心逻辑
   *
   * @param entityClass 实体类类型
   * @return TableMetadata
   */
  private static TableMetadata parseTableMetadata(Class<?> entityClass) {
    Table tableAnn = entityClass.getAnnotation(Table.class);
    if (tableAnn == null) {
      throw new MetadataException("@Table annotation missing on " + entityClass.getName());
    }

    String tableName = tableAnn.value();
    if (tableName == null || tableName.trim().isEmpty()) {
      tableName = entityClass.getSimpleName().toLowerCase();
    }
    List<ColumnMetadata> columns = parseColumns(entityClass);
    List<CascadeRelation> cascades = parseCascades(entityClass);

    log.debug("Parsed TableMetadata for table: {} with {} columns", tableName, columns.size());
    return new TableMetadata(tableName, columns, cascades);
  }

  /** 解析 {@code @Cascade} 集合（字段须同时 {@code @Transient}）。 */
  private static List<CascadeRelation> parseCascades(Class<?> entityClass) {
    List<CascadeRelation> cascades = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Class<?> current = entityClass;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        Cascade cascade = field.getAnnotation(Cascade.class);
        if (cascade == null || seen.contains(field.getName())) {
          continue;
        }
        if (!field.isAnnotationPresent(Transient.class)) {
          throw new MetadataException(
              "@Cascade on "
                  + entityClass.getName()
                  + "#"
                  + field.getName()
                  + " requires @Transient (collection is not a root table column)");
        }
        if (!Collection.class.isAssignableFrom(field.getType())) {
          throw new MetadataException(
              "@Cascade on "
                  + entityClass.getName()
                  + "#"
                  + field.getName()
                  + " must be a Collection");
        }
        Class<?> childType = resolveCollectionElementType(field);
        if (childType.getAnnotation(Table.class) == null) {
          throw new MetadataException(
              "@Cascade child type " + childType.getName() + " must be annotated with @Table");
        }
        String fk = cascade.foreignKey().trim();
        if (fk.isEmpty()) {
          throw new MetadataException(
              "@Cascade foreignKey must not be blank on "
                  + entityClass.getName()
                  + "#"
                  + field.getName());
        }
        try {
          childType.getDeclaredField(fk);
        } catch (NoSuchFieldException ex) {
          // 允许外键在父类上
          Class<?> walk = childType.getSuperclass();
          boolean found = false;
          while (walk != null && walk != Object.class) {
            try {
              walk.getDeclaredField(fk);
              found = true;
              break;
            } catch (NoSuchFieldException ignored) {
              walk = walk.getSuperclass();
            }
          }
          if (!found) {
            throw new MetadataException(
                "@Cascade foreignKey '"
                    + fk
                    + "' not found on child "
                    + childType.getName()
                    + " (field "
                    + field.getName()
                    + ")");
          }
        }
        cascades.add(new CascadeRelation(field.getName(), fk, childType));
        seen.add(field.getName());
      }
      current = current.getSuperclass();
    }
    return cascades;
  }

  private static Class<?> resolveCollectionElementType(Field field) {
    Type generic = field.getGenericType();
    if (!(generic instanceof ParameterizedType parameterized)) {
      throw new MetadataException(
          "@Cascade field "
              + field.getDeclaringClass().getName()
              + "#"
              + field.getName()
              + " must declare a concrete element type (e.g. List<OrderItem>)");
    }
    Type arg = parameterized.getActualTypeArguments()[0];
    if (!(arg instanceof Class<?> childType)) {
      throw new MetadataException(
          "@Cascade field " + field.getName() + " element type must be a concrete class");
    }
    return childType;
  }

  /** 解析实体类的列元数据（包括继承的字段） */
  private static List<ColumnMetadata> parseColumns(Class<?> entityClass) {
    List<ColumnMetadata> columns = new ArrayList<>();
    Set<String> processedFields = new HashSet<>();
    Class<?> currentClass = entityClass;

    // 遍历类及其父类，获取所有字段
    while (currentClass != null && currentClass != Object.class) {
      for (Field field : currentClass.getDeclaredFields()) {
        if (shouldProcessField(field, processedFields)) {
          columns.add(buildColumnMetadata(field));
          processedFields.add(field.getName());
        }
      }
      currentClass = currentClass.getSuperclass();
    }

    log.debug("Parsed {} columns from class {}", columns.size(), entityClass.getName());
    return columns;
  }

  /**
   * 判断字段是否需要处理（排除 static 字段、{@code @Transient} 注解和重复字段）。
   *
   * <p>{@code static} 常量（如 {@code private static final Money MAX_ORDER_AMOUNT}）不映射为列——其 ALL_CAPS
   * 命名经 camel→snake 转换会生成非法列名（曾产出 {@code ma_x__orde_r__amount} 导致 {@code
   * BadSqlGrammarException}）；静态字段也不属于聚合实例状态，任何情况下都不应参与持久化。
   */
  private static boolean shouldProcessField(Field field, Set<String> processedFields) {
    return !field.isAnnotationPresent(Transient.class)
        && !Modifier.isStatic(field.getModifiers())
        && !processedFields.contains(field.getName());
  }

  /**
   * {@code @Version} 字段的合法类型：{@link Number} 子类（Long/Integer/BigInteger…）或原始数值类型。
   *
   * <p><b>原始类型不是 {@link Number} 的子类</b>——{@code Number.class.isAssignableFrom(long.class)} 恒为
   * {@code false}。因此不能只用后者判定，否则 {@code @Version private long version;} / {@code private int
   * version;} 会在解析期被误拒（写入侧 {@code BaseRepository.toVersionValue} 本就支持这些类型）。
   */
  private static boolean isNumericVersionType(Class<?> type) {
    if (Number.class.isAssignableFrom(type)) {
      return true;
    }
    return type.isPrimitive() && type != boolean.class && type != char.class && type != void.class;
  }

  /** 构建 ColumnMetadata */
  private static ColumnMetadata buildColumnMetadata(Field field) {
    // @Version 必须是数值类型（Number 子类或原始数值）：乐观锁靠 version = version + 1 与 WHERE version = :old，
    // String/时间等非数值类型会在 SQL 期才炸且误导；解析期直接拒绝（ADR-0031 D1 评审）
    if (field.isAnnotationPresent(Version.class) && !isNumericVersionType(field.getType())) {
      throw new MetadataException(
          "@Version field must be a Number subtype: "
              + field.getDeclaringClass().getName()
              + "."
              + field.getName()
              + " ("
              + field.getType().getSimpleName()
              + ")");
    }

    Column columnAnn = field.getAnnotation(Column.class);
    String columnName = resolveColumnName(field, columnAnn);
    GenerationStrategy generationStrategy = resolveGenerationStrategy(field);
    String customGenerator = resolveCustomGenerator(field);
    String sequenceName = resolveSequenceName(field);

    return new ColumnMetadata(
        columnName,
        field.getName(),
        field.getType(),
        field.isAnnotationPresent(Id.class),
        field.isAnnotationPresent(Version.class),
        columnAnn != null && columnAnn.nullable(),
        columnAnn != null && columnAnn.unique(),
        columnAnn != null && columnAnn.autoGenerated()
            || (generationStrategy == GenerationStrategy.IDENTITY),
        field.isAnnotationPresent(Deleted.class),
        generationStrategy,
        customGenerator,
        sequenceName);
  }

  /** 解析列名，如果 Column 注解为空，则转换为蛇形命名 */
  private static String resolveColumnName(Field field, Column columnAnn) {
    return (columnAnn != null && StringUtils.hasText(columnAnn.name()))
        ? columnAnn.name().trim()
        : SqlUtil.toSnakeCase(field.getName());
  }

  /** 获取字段的生成策略 */
  private static GenerationStrategy resolveGenerationStrategy(Field field) {
    GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
    return (generatedValue != null) ? generatedValue.strategy() : null;
  }

  /** 处理主键的自定义生成器 */
  private static String resolveCustomGenerator(Field field) {
    GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
    return (generatedValue != null && StringUtils.hasText(generatedValue.generator()))
        ? generatedValue.generator().trim()
        : null;
  }

  /** 解析字段的序列生成策略 */
  private static String resolveSequenceName(Field field) {
    SequenceGenerator sequenceGen = field.getAnnotation(SequenceGenerator.class);
    return (sequenceGen != null) ? sequenceGen.sequenceName().trim() : null;
  }

  /** 清理缓存的方法，可以在应用重启时或需要时清除缓存 */
  public static void clearCache() {
    METADATA_CACHE.invalidateAll();
    log.info("All metadata cache cleared.");
  }

  /** 清理特定表的缓存 */
  public static void clearCacheForTable(String tableName) {
    METADATA_CACHE.invalidate(tableName);
    log.info("Cache for table {} cleared.", tableName);
  }
}
