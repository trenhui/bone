package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Mongo 连接器：基于 {@link MongoClient} 的真实调用（mongodb-driver-sync）。 */
@Component("MONGO_DB")
public class MongoClientImpl implements ExternalSystemClient {

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String uri = connectionString(config);
    try (MongoClient client = MongoClients.create(uri)) {
      client.getDatabase(adminDatabase(config)).runCommand(new Document("ping", 1));
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    String uri = connectionString(config);
    String database = databaseOf(params, config);
    String collection = collectionOf(params, config);
    String operation = operationOf(params, config);
    try (MongoClient client = MongoClients.create(uri)) {
      MongoDatabase db = client.getDatabase(database);
      MongoCollection<Document> coll = db.getCollection(collection);
      switch (operation.toUpperCase()) {
        case "INSERT" -> {
          Document doc = Document.parse(stringify(params.get("document")));
          coll.insertOne(doc);
          return Map.of("ok", true, "id", String.valueOf(doc.getObjectId("_id")));
        }
        case "DELETE" -> {
          Document filter = Document.parse(stringify(params.get("filter")));
          long deleted = coll.deleteMany(filter).getDeletedCount();
          return Map.of("deleted", deleted);
        }
        default -> {
          Document filter =
              params.get("filter") != null
                  ? Document.parse(stringify(params.get("filter")))
                  : new Document();
          return coll.find(filter).limit(100).into(new java.util.ArrayList<>());
        }
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Mongo 请求失败: " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getType() {
    return "MONGO_DB";
  }

  private static String connectionString(Map<String, Object> config) {
    String uri =
        config.get("connectionString") == null
            ? null
            : String.valueOf(config.get("connectionString"));
    if (uri != null && !uri.isBlank()) {
      return uri;
    }
    String host = config.get("host") == null ? "localhost" : String.valueOf(config.get("host"));
    Object portObj = config.get("port");
    int port = portObj == null ? 27017 : Integer.parseInt(String.valueOf(portObj));
    String database =
        config.get("database") == null ? "admin" : String.valueOf(config.get("database"));
    return "mongodb://" + host + ":" + port + "/" + database;
  }

  private static String adminDatabase(Map<String, Object> config) {
    return config.get("database") == null ? "admin" : String.valueOf(config.get("database"));
  }

  private static String databaseOf(Map<String, Object> params, Map<String, Object> config) {
    Object db = params != null ? params.get("database") : null;
    if (db == null) {
      db = config.get("database");
    }
    return db == null ? "admin" : String.valueOf(db);
  }

  private static String collectionOf(Map<String, Object> params, Map<String, Object> config) {
    Object col = params != null ? params.get("collection") : null;
    if (col == null) {
      col = config.get("collection");
    }
    return col == null ? "default" : String.valueOf(col);
  }

  private static String operationOf(Map<String, Object> params, Map<String, Object> config) {
    Object op = params != null ? params.get("operation") : null;
    if (op == null) {
      op = config.get("operation");
    }
    return op == null ? "FIND" : String.valueOf(op);
  }

  private static String stringify(Object value) {
    if (value instanceof String s) {
      return s;
    }
    return String.valueOf(value);
  }
}
