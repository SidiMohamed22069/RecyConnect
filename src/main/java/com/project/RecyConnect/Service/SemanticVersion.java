package com.project.RecyConnect.Service;

import java.util.regex.Pattern;

/**
 * Une version applicative comparable, au sens de <a href="https://semver.org">semver</a>.
 *
 * <p>Sert ici a valider la configuration avant de la publier: comparer
 * {@code app.version.minimum} et {@code app.version.latest} comme des chaines
 * ferait passer {@code 1.10.0} pour anterieure a {@code 1.3.0} — {@code '0'}
 * precede {@code '3'} dans l'ordre lexicographique — et le service annoncerait
 * une politique incoherente.
 *
 * <p>Les metadonnees de build ({@code +42}) sont ignorees, comme le veut la
 * specification. Les pre-publications ({@code 1.3.0-beta}) sont acceptees a
 * l'analyse et precedent la version stable de meme numero.
 */
public record SemanticVersion(int major, int minor, int patch, String preRelease)
        implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN = Pattern.compile(
            "^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    /**
     * Analyse une version, ou rend {@code null} si le texte n'en est pas une.
     *
     * <p>Ne leve jamais: l'appelant est une configuration, et une configuration
     * illisible doit produire un avertissement, pas un demarrage en echec.
     */
    public static SemanticVersion parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        var matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return new SemanticVersion(major, minor, patch, matcher.group(4));
        } catch (NumberFormatException e) {
            // Nombre au-dela de int: ce n'est pas un numero de version d'application.
            return null;
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        int byPatch = Integer.compare(patch, other.patch);
        if (byPatch != 0) {
            return byPatch;
        }
        return comparePreRelease(preRelease, other.preRelease);
    }

    /** Semver §11.4: une version stable l'emporte sur sa pre-publication. */
    private static int comparePreRelease(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < left.length && i < right.length; i++) {
            if (left[i].equals(right[i])) {
                continue;
            }
            boolean leftNumeric = left[i].matches("\\d+");
            boolean rightNumeric = right[i].matches("\\d+");
            if (leftNumeric && rightNumeric) {
                return Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
            }
            if (leftNumeric) {
                return -1;
            }
            if (rightNumeric) {
                return 1;
            }
            return left[i].compareTo(right[i]);
        }
        return Integer.compare(left.length, right.length);
    }

    @Override
    public String toString() {
        String core = major + "." + minor + "." + patch;
        return preRelease == null ? core : core + "-" + preRelease;
    }
}
