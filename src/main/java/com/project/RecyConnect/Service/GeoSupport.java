package com.project.RecyConnect.Service;

/**
 * Les calculs geographiques du catalogue: distance, arrondi de protection,
 * rectangle englobant.
 *
 * <p>Des fonctions pures, sans etat ni dependance: elles se verifient seules,
 * et la meme arithmetique existe cote mobile ({@code core/utils/geo.dart}). Les
 * deux doivent rester d'accord — le mobile arrondit ce qu'il affiche, le
 * serveur arrondit ce qu'il envoie, et un arrondi deja applique doit rendre le
 * meme point plutot que de deriver a chaque passage.
 */
public final class GeoSupport {

    private GeoSupport() {
    }

    /** Le centre de Nouakchott, repere par defaut. */
    public static final double NOUAKCHOTT_LAT = 18.0790;
    public static final double NOUAKCHOTT_LNG = -15.9650;

    /** Rayon moyen de la Terre, en kilometres. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Un degre de latitude, en metres — constant sur toute la planete. */
    private static final double METERS_PER_DEGREE_LAT = 111320.0;

    /**
     * Le pas de la grille d'arrondi, en metres.
     *
     * <p>Trois cents metres: assez pour savoir dans quelle rue chercher, trop
     * peu pour designer une maison.
     */
    public static final double BLUR_GRID_METERS = 300.0;

    /**
     * La distance a vol d'oiseau entre deux points, en kilometres.
     *
     * <p>Formule de haversine. A l'echelle de Nouakchott, l'ecart avec une
     * geodesique d'ellipsoide se compte en metres: sans commune mesure avec
     * l'imprecision d'un point GPS de telephone, et sans interet pour repondre
     * a "est-ce que j'y vais ?".
     */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Le point ramene au centre de sa case de grille, d'environ
     * {@link #BLUR_GRID_METERS} de cote.
     *
     * <p>Rend {@code [latitude, longitude]}.
     *
     * <p>Le pas en longitude se calcule sur la latitude <b>deja arrondie</b>, et
     * non sur celle du point. Ce n'est pas un detail de precision: avec la
     * latitude brute, deux voisins d'une meme case obtiennent des pas
     * legerement differents, donc deux points arrondis differents — a quelques
     * dizaines de centimetres pres. L'arrondi promet que les habitants d'une
     * meme case deviennent indistinguables; cet ecart residuel suffisait a les
     * distinguer, et donc a remonter vers la position d'origine.
     *
     * <p>Consequence utile: la fonction est idempotente. Arrondir un point deja
     * arrondi rend le meme point, ce qui permet au mobile d'appliquer la meme
     * regle sans deriver.
     */
    public static double[] blur(double latitude, double longitude) {
        double latStep = BLUR_GRID_METERS / METERS_PER_DEGREE_LAT;
        double blurredLat = snap(latitude, latStep);

        double metersPerDegreeLng =
                METERS_PER_DEGREE_LAT * Math.abs(Math.cos(Math.toRadians(blurredLat)));
        double lngStep = metersPerDegreeLng < 1.0
                ? latStep
                : BLUR_GRID_METERS / metersPerDegreeLng;

        return new double[] { blurredLat, snap(longitude, lngStep) };
    }

    private static double snap(double value, double step) {
        return Math.floor(value / step) * step + step / 2;
    }

    /**
     * De combien de degres de latitude il faut s'ecarter pour couvrir
     * {@code km} kilometres.
     *
     * <p>Sert a border une recherche par rayon avant le calcul exact: filtrer
     * d'abord sur un rectangle est ce qui permet a la base de se servir d'un
     * index, la ou une distance calculee sur chaque ligne l'en empeche.
     */
    public static double latitudeDegreesFor(double km) {
        return km / (METERS_PER_DEGREE_LAT / 1000.0);
    }

    /** Idem en longitude, a une latitude donnee. */
    public static double longitudeDegreesFor(double km, double atLatitude) {
        double kmPerDegree =
                (METERS_PER_DEGREE_LAT / 1000.0) * Math.abs(Math.cos(Math.toRadians(atLatitude)));
        // Aux poles, un degre de longitude ne couvre plus rien: on rend le tour
        // complet plutot qu'une division par zero.
        return kmPerDegree < 0.001 ? 180.0 : km / kmPerDegree;
    }

    /** Vrai si le couple est une position plausible. */
    public static boolean isValid(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        if (latitude.isNaN() || longitude.isNaN()) {
            return false;
        }
        // (0, 0) est presque toujours un champ non renseigne plutot qu'un point
        // dans le golfe de Guinee.
        if (latitude == 0.0 && longitude == 0.0) {
            return false;
        }
        return Math.abs(latitude) <= 90.0 && Math.abs(longitude) <= 180.0;
    }
}
