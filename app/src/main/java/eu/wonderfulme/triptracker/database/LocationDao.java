package eu.wonderfulme.triptracker.database;

import android.arch.persistence.db.SupportSQLiteQuery;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.RawQuery;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    void insertSingleRecord(LocationData data);

    @Query("SELECT * FROM LocationData WHERE item_key = :itemKey")
    List<LocationData> getDbData(int itemKey);

    @Query("DELETE FROM LocationData WHERE item_key = :itemKey")
    void deleteSingleItemKey(int itemKey);

    @Query("SELECT item_key FROM LocationData ORDER BY item_key DESC LIMIT 1")
    int getLastItemKey();

    @Query("DELETE FROM LocationData WHERE timestamp <= :checkTime")
    void nukeRowsMoreThan30Days(String checkTime);

    //TODO I dont know why this query is not working! So I have to do it manually!
    @Query("SELECT item_key, MIN(timestamp) FROM LocationData GROUP BY item_key")
    List<LocationHeaderData> getLocationHeaderData();
}
