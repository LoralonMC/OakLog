package dev.oakheart.oaklog.storage;

public interface DataStore {

    boolean initialize();

    int getSchemaVersion();

    void close();
}
