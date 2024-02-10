package info.mandarini.busbooking.persistence.repositories;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import info.mandarini.busbooking.persistence.entities.Fermata;

@Database(entities = {Fermata.class}, version = 1)
public abstract class FavoritesDataBase extends RoomDatabase {
    public abstract FermateDao fermataDao();

    private static FavoritesDataBase instance = null;
    private static final Object sLock = new Object();

    public static FavoritesDataBase getInstance(Context context) {
        if (instance == null) {
            synchronized (sLock) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),FavoritesDataBase.class,"Favorites.db").allowMainThreadQueries().build();
                }
            }
        }
        return instance;
    }

}

