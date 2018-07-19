package eu.wonderfulme.triptracker.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    void insertSingleRecord(LocationData data);

    @Query("SELECT * FROM LocationData")
    List<LocationData> getAllDbData();

    @Query("DELETE FROM LocationData")
    void deleteAllRecords();
}
