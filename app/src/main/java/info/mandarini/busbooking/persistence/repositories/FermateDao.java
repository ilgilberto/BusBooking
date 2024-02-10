package info.mandarini.busbooking.persistence.repositories;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import info.mandarini.busbooking.persistence.entities.Fermata;

@Dao
public interface FermateDao {

    @Insert
    void insertAll(Fermata... fermate);

    @Insert
    long save(Fermata fermata);

    @Delete
    void delete(Fermata fermata);

    @Query("SELECT * FROM FERMATE")
    List<Fermata> selectAll();

    @Query("SELECT * FROM FERMATE WHERE CODICE_FERMATA=:codice")
    Fermata getFromCodice(String codice);


}
