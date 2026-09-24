package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.enums.Enums.NomCategorieTicket;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dédié — NE FAIT PAS PARTIE de la suite de non-régression fonctionnelle. Sert
 * uniquement à extraire sur disque le PNG produit par
 * {@link BilletterieService#genererImageBillet} (méthode privée, appelée par réflexion
 * pour ne rien changer à sa visibilité) afin de comparer visuellement le rendu serveur
 * au visuel de référence côté client (ticket-image.service.ts). Correctif retour client :
 * les billets reçus par e-mail doivent rester identiques au format téléchargeable.
 */
class BilletterieServiceImageBilletDumpTest {

    @Test
    void genereEtSauvegardeUnBilletDeReferencePourComparaisonVisuelle() throws Exception {
        SoireeEvent soiree = SoireeEvent.builder()
                .id(UUID.randomUUID())
                .nom("Finale Night Karaoke Stars — Saison 2026")
                .dateHeure(ZonedDateTime.parse("2026-09-14T16:00:00+00:00").toInstant())
                .lieu("La Terrasse, Ouagadougou")
                .build();

        CategorieTicket categorie = CategorieTicket.builder()
                .id(UUID.randomUUID())
                .soiree(soiree)
                .nom(NomCategorieTicket.PARTENAIRE)
                .prix(BigDecimal.ZERO)
                .build();

        Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .soiree(soiree)
                .telephoneReservant("+22670000000")
                .nomReservant("Haïdar Abdoul Nasser")
                .emailReservant("haidarabdoulnasser@gmail.com")
                .nbPlaces(2)
                .montantTotal(BigDecimal.ZERO)
                .statut(StatutReservation.CONFIRMEE)
                .gratuit(true)
                .build();

        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .reservation(reservation)
                .soiree(soiree)
                .categorie(categorie)
                .nomSpectateur(reservation.getNomReservant())
                .telephoneSpectateur(reservation.getTelephoneReservant())
                .statut(StatutTicket.EMIS)
                .dateEmission(Instant.now())
                .build();

        QRCodeTicket qr = QRCodeTicket.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .codeUuid(UUID.randomUUID())
                .valide(true)
                .build();

        BilletterieService service = new BilletterieService(
                null, null, null, null, null, null, null, null, null, null);

        Method genererImageBillet = BilletterieService.class.getDeclaredMethod(
                "genererImageBillet", Reservation.class, QRCodeTicket.class, int.class, int.class);
        genererImageBillet.setAccessible(true);

        byte[] png = (byte[]) genererImageBillet.invoke(service, reservation, qr, 1, 2);
        assertThat(png).isNotEmpty();

        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(480);
        assertThat(image.getHeight()).isEqualTo(800);

        File sortie = new File(System.getProperty(
                "nks.billet.dump.path",
                System.getProperty("java.io.tmpdir") + "/nks-billet-reference.png"));
        ImageIO.write(image, "png", sortie);
        System.out.println("Billet de test écrit dans : " + sortie.getAbsolutePath());
    }
}
