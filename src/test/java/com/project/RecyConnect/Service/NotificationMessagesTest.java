package com.project.RecyConnect.Service;

import com.project.RecyConnect.Config.NotificationMessagesConfig;
import com.project.RecyConnect.Model.SupportedLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La table de traduction elle-meme, sans base ni contexte Spring.
 *
 * <p>Ce qui se joue ici tient en une phrase: aucune combinaison de langue et de
 * donnee manquante ne doit produire un texte vide, un "null" affiche, ou une
 * exception. Une notification est souvent la seule chose qui previent un
 * vendeur qu'une offre l'attend; elle doit partir meme degradee.
 */
class NotificationMessagesTest {

    private NotificationMessages messages;

    @BeforeEach
    void setUp() {
        messages = new NotificationMessages(
                new NotificationMessagesConfig().notificationMessageSource());
    }

    @Test
    @DisplayName("le francais reste la langue de reference")
    void francais() {
        NotificationMessages.Text text =
                messages.textFor("OFFER_ACCEPTED", SupportedLanguage.FR, "Cartons");

        assertEquals("Offre acceptée", text.title());
        assertEquals("Votre offre sur Cartons a été acceptée", text.body());
    }

    @Test
    @DisplayName("l'arabe est servi depuis notifications_ar.properties, en UTF-8")
    void arabe() {
        NotificationMessages.Text text =
                messages.textFor("OFFER_ACCEPTED", SupportedLanguage.AR, "Cartons");

        assertEquals("تم قبول العرض", text.title());
        assertEquals("تم قبول عرضك على Cartons", text.body());
    }

    @Test
    @DisplayName("l'anglais aussi")
    void anglais() {
        NotificationMessages.Text text =
                messages.textFor("OFFER_ACCEPTED", SupportedLanguage.EN, "Cartons");

        assertEquals("Offer accepted", text.title());
        assertEquals("Your offer on Cartons was accepted", text.body());
    }

    @Test
    @DisplayName("les emplacements sont remplis dans l'ordre, y compris en arabe")
    void plusieursArguments() {
        NotificationMessages.Text francais =
                messages.textFor("OFFER_RECEIVED", SupportedLanguage.FR, "Ahmed", "Cartons");
        assertEquals("Ahmed vous a fait une offre pour : Cartons", francais.body());

        NotificationMessages.Text arabe =
                messages.textFor("OFFER_RECEIVED", SupportedLanguage.AR, "Ahmed", "Cartons");
        assertEquals("قدّم Ahmed عرضًا على: Cartons", arabe.body());
    }

    @Test
    @DisplayName("une langue nulle ou inconnue retombe sur le francais")
    void repliSurLeFrancais() {
        String attendu = messages.textFor("OFFER_ACCEPTED", SupportedLanguage.FR, "Cartons").body();

        assertEquals(attendu,
                messages.textFor("OFFER_ACCEPTED", (SupportedLanguage) null, "Cartons").body());
        assertEquals(attendu,
                messages.textFor("OFFER_ACCEPTED", (String) null, "Cartons").body());
        assertEquals(attendu,
                messages.textFor("OFFER_ACCEPTED", "klingon", "Cartons").body());
        assertEquals(attendu,
                messages.textFor("OFFER_ACCEPTED", "", "Cartons").body());
    }

    @Test
    @DisplayName("une etiquette regionale est ramenee a sa langue")
    void etiquetteRegionale() {
        assertEquals(messages.textFor("OFFER_ACCEPTED", SupportedLanguage.AR, "Cartons").title(),
                messages.textFor("OFFER_ACCEPTED", "ar-MR", "Cartons").title());
        assertEquals(messages.textFor("OFFER_ACCEPTED", SupportedLanguage.EN, "Cartons").title(),
                messages.textFor("OFFER_ACCEPTED", "EN_US", "Cartons").title());
    }

    @Test
    @DisplayName("un argument manquant devient un terme traduit, jamais \"null\"")
    void argumentManquant() {
        NotificationMessages.Text francais =
                messages.textFor("QUEUE_UPDATED", SupportedLanguage.FR, (Object) null);
        assertFalse(francais.body().contains("null"),
                "MessageFormat imprime litteralement \"null\" si on le laisse faire");
        assertTrue(francais.body().contains("un élément"));

        NotificationMessages.Text arabe =
                messages.textFor("QUEUE_UPDATED", SupportedLanguage.AR, (Object) null);
        assertFalse(arabe.body().contains("null"));
        assertTrue(arabe.body().contains("عنصر"));
    }

    @Test
    @DisplayName("chaque type connu porte un titre et un corps dans les trois langues")
    void aucunLibelleManquant() {
        String[] types = {
                "OFFER_RECEIVED", "OFFER_ACCEPTED", "OFFER_REJECTED", "OFFER_REFUSED",
                "OFFER_UPDATED", "OFFER_CANCELLED", "OFFER_AUTO_CANCELLED_STOCK",
                "OUTBID_BY_BETTER_OFFER", "QUEUE_UPDATED",
                "PRODUCT_APPROVED", "NEW_MESSAGE", "TEST",
        };

        for (SupportedLanguage langue : SupportedLanguage.values()) {
            for (String type : types) {
                NotificationMessages.Text text = messages.textFor(type, langue, "X", "Y");

                assertNotNull(text.title());
                assertFalse(text.title().isBlank(),
                        type + " n'a pas de titre en " + langue.getCode());
                assertFalse(text.body().isBlank(),
                        type + " n'a pas de corps en " + langue.getCode());
                assertNotEqualsType(type, text.title(), langue);
            }
        }
    }

    /**
     * Un libelle absent fait rendre le code du type: c'est le signal a traquer.
     */
    private void assertNotEqualsType(String type, String title, SupportedLanguage langue) {
        assertFalse(type.equals(title),
                "aucun libelle pour " + type + " en " + langue.getCode());
    }

    @Test
    @DisplayName("un type inconnu ne fait pas echouer l'envoi")
    void typeInconnu() {
        NotificationMessages.Text text =
                messages.textFor("TYPE_QUI_N_EXISTE_PAS", SupportedLanguage.FR);

        assertEquals("TYPE_QUI_N_EXISTE_PAS", text.title());
        assertEquals("", text.body());
    }
}
