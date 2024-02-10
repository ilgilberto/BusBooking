package info.mandarini.busbooking.persistence.entities;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "FERMATE")
public class Fermata {


        @PrimaryKey(autoGenerate = true)
        public Integer uid;

        @ColumnInfo(name = "CODICE_FERMATA")
        public String codice;

        @ColumnInfo(name = "DENOMINAZIONE")
        public String descrizione;

        @ColumnInfo(name = "UBICAZIONE")
        public String ubicazione;


        @ColumnInfo(name = "last_name")
        public String lastName;


}
