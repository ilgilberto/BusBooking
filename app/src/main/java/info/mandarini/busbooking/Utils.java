package info.mandarini.busbooking;

import info.mandarini.busbooking.persistence.entities.Fermata;

public class Utils {

    private Utils() {
        super();
    }

    public static String print(Fermata fermata) {
        if (fermata.alias != null) {
            return fermata.alias;
        }
        else {
            return fermata.codice + "-"+fermata.descrizione;
        }
    }

}
