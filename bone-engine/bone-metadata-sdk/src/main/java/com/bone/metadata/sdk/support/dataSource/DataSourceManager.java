package com.bone.metadata.sdk.support.dataSource;

import javax.sql.DataSource;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Data source manager interface that defines core functionalities for data source registration,
 * retrieval, switching, and health checking. It provides a unified abstraction for multi-data source
 * management with thread-safe operations.
 */
public interface DataSourceManager {
    
    /**
     * Registers a data source with the manager.
     * 
     * @param name the name of the data source
     * @param dataSource the data source instance
     * @throws IllegalArgumentException if the name is null/empty or data source is null
     * @throws IllegalStateException if the data source already exists or maximum limit is exceeded
     */
    void registerDataSource(String name, DataSource dataSource);
    
    /**
     * Unregisters an existing data source.
     * 
     * @param name the name of the data source to remove
     * @return true if the data source was found and removed; false otherwise
     */
    boolean unregisterDataSource(String name);
    
    /**
     * Retrieves the data source with the specified name.
     * 
     * @param name the name of the data source
     * @return the data source instance, or null if not found in non-strict mode
     * @throws IllegalArgumentException if the data source does not exist in strict mode
     */
    DataSource getDataSource(String name);
    
    /**
     * Gets the currently active data source.
     * 
     * @return the currently active data source instance
     * @throws IllegalStateException if no data source is available in strict mode
     */
    DataSource getCurrentDataSource();
    
    /**
     * Switches the current active data source.
     * 
     * @param name the name of the data source to switch to
     * @return true if the switch was successful; false otherwise
     */
    boolean switchDataSource(String name);
    
    /**
     * Resets the current active data source to the default data source.
     */
    void resetDataSource();
    
    /**
     * Gets all registered data source names.
     * 
     * @return a set of data source names
     */
    Set<String> getAllDataSourceNames();
    
    /**
     * Checks if the specified data source is healthy and available.
     * 
     * @param name the name of the data source to check
     * @return true if the data source exists and is available; false otherwise
     */
    boolean isDataSourceHealthy(String name);
    
    /**
     * Gets the name of the currently active data source.
     * 
     * @return the current data source name
     */
    String getCurrentDataSourceName();
    
    /**
     * Executes an action with a specific data source and returns the result.
     * This method ensures that the data source is properly switched back to the original one
     * after execution, even if an exception occurs.
     * 
     * @param dataSourceName the name of the data source to use
     * @param action the action to execute
     * @param <T> the return type of the action
     * @return the result of the action
     */
    default <T> T executeWithDataSource(String dataSourceName, Supplier<T> action) {
        String originalDataSource = getCurrentDataSourceName();
        try {
            switchDataSource(dataSourceName);
            return action.get();
        } finally {
            if (originalDataSource != null) {
                switchDataSource(originalDataSource);
            }
        }
    }
    
    /**
     * Sets the name of the default data source.
     * 
     * @param defaultDataSourceName the name of the default data source
     */
    void setDefaultDataSourceName(String defaultDataSourceName);
    
    /**
     * Sets the strict mode flag.
     * When enabled, operations will throw exceptions for invalid states rather than returning null.
     * 
     * @param strictMode whether to enable strict mode
     */
    void setStrictMode(boolean strictMode);
}