package info.mandarini.busbooking.persistence.repositories;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

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

    /**
     * Aggiorna l'alias della fermata identificata dal codice.
     *
     * @return numero di righe aggiornate (0 se la fermata non esiste)
     */
    @Query("UPDATE FERMATE SET ALIAS=:alias WHERE CODICE_FERMATA=:codice")
    int updateAlias(String codice, String alias);

    /**
     * Update generico (non strettamente necessario per l'alias, ma utile in futuro).
     */
    @Update
    int update(Fermata fermata);


}
