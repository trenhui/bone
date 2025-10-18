package com.bone.metadata.sdk.support.cache;

import java.util.logging.Logger;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A thread-safe static utility class for caching SQL fragments.
 * Supports both class-specific and global fragment access.
 * - RepositoryFactoryBean initializes fragments from @SqlFragment annotations.
 * - MyBatisSqlProcessor reads fragments and adds fragments from <sql> tags.
 */
public final class FragmentCache {

    private static final Logger LOGGER = Logger.getLogger(FragmentCache.class.getName());

    // Class-specific cache: className -> (fragmentId -> sql)
    private static final ConcurrentMap<String, Map<String, String>> byClass = new ConcurrentHashMap<>();

    // Global cache: fragmentId -> sql
    private static final ConcurrentMap<String, String> global = new ConcurrentReferenceHashMap<>(64);

    // Tracks initialized classes to avoid redundant processing
    private static final Set<String> initializedClasses = ConcurrentHashMap.newKeySet();

    private FragmentCache() {
        // Prevent instantiation
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * Checks if fragments for a class have been initialized.
     *
     * @param className The fully qualified class name.
     * @return true if initialized, false otherwise.
     */
    public static boolean isInitialized(String className) {
        if (!StringUtils.hasText(className)) {
            LOGGER.info("Class name is empty, not initialized");
            return false;
        }
        return initializedClasses.contains(className);
    }

    /**
     * Marks a class as initialized.
     *
     * @param className The fully qualified class name.
     */
    public static void markInitialized(String className) {
        if (!StringUtils.hasText(className)) {
            LOGGER.warning("Attempted to mark empty class name as initialized");
            return;
        }
        initializedClasses.add(className);
        LOGGER.info("Marked class as initialized: " + className);
    }

    /**
     * Caches a batch of SQL fragments for a class.
     *
     * @param className The fully qualified class name.
     * @param fragments A map of fragment IDs to SQL content.
     */
    public static void putClassFragments(String className, Map<String, String> fragments) {
        if (!StringUtils.hasText(className)) {
            LOGGER.warning("Cannot cache fragments for empty class name");
            return;
        }
        if (fragments == null || fragments.isEmpty()) {
            LOGGER.info("No fragments to cache for class: " + className);
            markInitialized(className);
            return;
        }

        Map<String, String> immutableFragments = Collections.unmodifiableMap(new ConcurrentHashMap<>(fragments));
        byClass.put(className, immutableFragments);
        global.putAll(fragments);
        markInitialized(className);
        LOGGER.info("Cached " + fragments.size() + " SQL fragments for class: " + className);
    }

    /**
     * Adds a single SQL fragment to the cache.
     *
     * @param className The fully qualified class name (or "<inline>" for templates without a class).
     * @param fragmentId The fragment ID.
     * @param sql The SQL content.
     */
    public static void putFragment(String className, String fragmentId, String sql) {
        if (!StringUtils.hasText(className) || !StringUtils.hasText(fragmentId) || !StringUtils.hasText(sql)) {
            LOGGER.warning("Invalid fragment: className=" + className + ", fragmentId=" + fragmentId + ", sql=" + sql);
            return;
        }

        Map<String, String> classFragments = byClass.computeIfAbsent(className, k -> new ConcurrentHashMap<>());
        if (classFragments.putIfAbsent(fragmentId, sql.trim()) == null) {
            global.putIfAbsent(fragmentId, sql.trim());
            LOGGER.info("Added SQL fragment: class=" + className + ", id=" + fragmentId);
        } else {
            LOGGER.warning("Duplicate SQL fragment id '" + fragmentId + "' for class: " + className);
        }
    }

    /**
     * Retrieves all fragments for a class.
     *
     * @param className The fully qualified class name.
     * @return An unmodifiable map of fragment IDs to SQL content, or empty map if none.
     */
    public static Map<String, String> getClassFragments(String className) {
        if (!StringUtils.hasText(className)) {
            LOGGER.info("Cannot retrieve fragments for empty class name");
            return Collections.emptyMap();
        }
        Map<String, String> fragments = byClass.get(className);
        return fragments != null ? Collections.unmodifiableMap(fragments) : Collections.emptyMap();
    }

    /**
     * Retrieves a fragment by its ID, searching globally.
     *
     * @param fragmentId The fragment ID.
     * @return The SQL content, or null if not found.
     */
    public static String getById(String fragmentId) {
        if (!StringUtils.hasText(fragmentId)) {
            LOGGER.info("Cannot retrieve fragment for empty ID");
            return null;
        }
        String fragment = global.get(fragmentId);
        if (fragment != null) {
            LOGGER.info("Retrieved SQL fragment: id=" + fragmentId);
        } else {
            LOGGER.info("SQL fragment not found: id=" + fragmentId);
        }
        return fragment;
    }

    /**
     * Clears all cached fragments and initialized class markers.
     */
    public static void clearAll() {
        byClass.clear();
        global.clear();
        initializedClasses.clear();
        LOGGER.info("All SQL fragment caches cleared");
    }

    /**
     * Retrieves all fragment IDs for debugging or testing purposes.
     *
     * @return A set of all fragment IDs in the global cache.
     */
    public static Set<String> getAllFragmentIds() {
        return Collections.unmodifiableSet(global.keySet());
    }
}