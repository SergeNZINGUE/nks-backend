package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.billetterie.ReservationRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationResponse;
import bf.laterrasse.nks.dto.billetterie.TicketAvecQrResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.event.PaiementEchoueEvent;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.email.EmailGateway;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.*;
import bf.laterrasse.nks.security.TicketAccessTokenService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WF-09/WF-10/WF-14. Remboursement billetterie décidé avec le client : traitement MANUEL
 * au cas par cas (pas d'automatisation, cf. README §Décisions) — annulerReservation()
 * libère la place et notifie, mais ne déclenche jamais PaymentGateway.rembourserTransaction().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BilletterieService {

    private static final ZoneId FUSEAU_OUAGA = ZoneId.of("Africa/Ouagadougou");
    private static final DateTimeFormatter FORMAT_DATE_BILLET =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    // Visuel billet "badge FESPACO" (correctif retour client 14/09/2026) — même palette et
    // mise en page que nks-frontend/src/app/core/services/ticket-image.service.ts, pour que
    // le billet reçu par e-mail soit identique au billet téléchargeable côté client.
    private static final int LARGEUR_BILLET = 480;
    private static final int HAUTEUR_BILLET = 800;
    private static final Color NKS_BG_PRIMARY = new Color(0x0D, 0x0D, 0x1E);
    private static final Color NKS_BG_SURFACE = new Color(0x1A, 0x1A, 0x2E);
    private static final Color NKS_GOLD = new Color(0xC9, 0xA2, 0x27);
    private static final Color NKS_GOLD_LIGHT = new Color(0xE8, 0xC0, 0x4A);
    private static final Color NKS_TEXT = Color.WHITE;
    private static final Color NKS_TEXT_SECONDARY = new Color(0xC8, 0xC8, 0xD0);
    private static final Color NKS_BANDE_CONTOUR = new Color(201, 162, 39, 90);
    private static final Color NKS_FOOTER_TEXT = new Color(13, 13, 30, 191);

    private static volatile BufferedImage logoNksCache;
    private static volatile BufferedImage logoTerrasseCache;

    private final CategorieTicketRepository categorieTicketRepository;
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final QRCodeTicketRepository qrCodeTicketRepository;
    private final PaiementService paiementService;
    private final ParametrePlateformeService parametrePlateformeService;
    private final NotificationService notificationService;
    private final TicketAccessTokenService ticketAccessTokenService;

    @Value("${nks.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public ReservationResponse initierReservation(ReservationRequest request) {
        CategorieTicket categorie = categorieTicketRepository.findByIdForUpdate(request.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de ticket introuvable"));

        if (!categorie.isActif()) {
            throw new ConflitEtatException("Cette catégorie de billets n'est plus ouverte à la réservation");
        }
        int placesRestantes = categorie.getNbPlacesDisponibles() - categorie.getNbPlacesReservees();
        if (placesRestantes < request.nbPlaces()) {
            throw new ConflitEtatException("Places insuffisantes : " + placesRestantes + " restante(s)");
        }

        BigDecimal montant = categorie.getPrix().multiply(BigDecimal.valueOf(request.nbPlaces()));
        int delaiMinutes = parametrePlateformeService.getInt("DELAI_PRERESA_MINUTES", 15);
        // Correctif bug UX (recherche "mes tickets") : toute nouvelle réservation est stockée
        // en E.164, pour que la recherche par téléphone local/international soit cohérente.
        String telephoneNormalise = SmsGateway.normaliserTelephone(request.telephoneReservant());

        Reservation reservation = Reservation.builder()
                .soiree(categorie.getSoiree())
                .telephoneReservant(telephoneNormalise)
                .nomReservant(request.nomReservant())
                .emailReservant(request.emailReservant())
                .nbPlaces(request.nbPlaces())
                .montantTotal(montant)
                .statut(StatutReservation.PENDING)
                .dateExpiration(Instant.now().plus(delaiMinutes, ChronoUnit.MINUTES))
                .gratuit(montant.compareTo(BigDecimal.ZERO) == 0)
                .build();

        // Pré-réservation immédiate des places (libérées par ReservationExpiryJob si non payées à temps)
        categorie.setNbPlacesReservees(categorie.getNbPlacesReservees() + request.nbPlaces());
        categorieTicketRepository.save(categorie);

        String urlPaiement = null;
        if (!reservation.isGratuit()) {
            PaiementInitie paiementInitie;
            try {
                paiementInitie = paiementService.creerEtDemarrer(
                        TypePaiement.BILLET, montant, telephoneNormalise, null);
            } catch (Exception e) {
                // La réservation n'a pas encore été persistée à ce stade : ReservationExpiryJob
                // ne peut pas la retrouver pour libérer la place, donc on la libère nous-mêmes ici
                // (même geste que liberer()) avant de propager une erreur métier au client.
                categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - request.nbPlaces()));
                categorieTicketRepository.save(categorie);
                log.error("Échec de l'initialisation du paiement pour la réservation (catégorie {}, {} place(s))",
                        categorie.getId(), request.nbPlaces(), e);
                throw new ValidationMetierException(
                        "Le service de paiement est momentanément indisponible. Réessayez dans quelques instants.");
            }
            reservation.setPaiement(paiementInitie.paiement());
            urlPaiement = paiementInitie.urlPaiement();
        } else {
            reservation.setStatut(StatutReservation.CONFIRMEE);
        }

        reservation = reservationRepository.save(reservation);

        if (reservation.isGratuit()) {
            genererTickets(reservation, categorie);
        }

        // Jeton post-achat (scope "read" seul, jamais "cancel") scopé à CETTE réservation —
        // correctif audit IDOR : couvre le délai de confirmation asynchrone du paiement
        // (tickets pas encore générés au moment de l'émission mais existeront avant expiration).
        Duration dureeAccessToken = Duration.ofMinutes(delaiMinutes + 10);
        String ticketAccessToken = ticketAccessTokenService.emettre(
                telephoneNormalise, Set.of("read"), reservation.getId(), dureeAccessToken);

        return new ReservationResponse(reservation.getId(),
                reservation.getPaiement() != null ? reservation.getPaiement().getId() : null,
                urlPaiement, montant, reservation.getStatut().name(),
                ticketAccessToken, dureeAccessToken.toSeconds());
    }

    @EventListener
    @Transactional
    public void onPaiementConfirme(PaiementConfirmeEvent event) {
        if (event.typePaiement() != TypePaiement.BILLET) {
            return;
        }
        reservationRepository.findByPaiementId(event.paiementId())
                .ifPresent(reservation -> {
                    reservation.setStatut(StatutReservation.CONFIRMEE);
                    reservationRepository.save(reservation);
                    CategorieTicket categorie = trouverCategoriePourReservation(reservation);
                    genererTickets(reservation, categorie);
                });
    }

    @EventListener
    @Transactional
    public void onPaiementEchoue(PaiementEchoueEvent event) {
        if (event.typePaiement() != TypePaiement.BILLET) {
            return;
        }
        reservationRepository.findByPaiementId(event.paiementId()).ifPresent(this::liberer);
    }

    @Transactional
    public void annulerReservation(UUID reservationId, String telephoneAppelant) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);

        if (reservation == null || !SmsGateway.normaliserTelephone(reservation.getTelephoneReservant())
                .equals(SmsGateway.normaliserTelephone(telephoneAppelant))) {
            throw new AccesRefuseException("Réservation non associée à ce numéro de téléphone");
        }

        if (reservation.getStatut() != StatutReservation.CONFIRMEE) {
            throw new ConflitEtatException("Seule une réservation confirmée peut être annulée");
        }
        long heuresAvant = ChronoUnit.HOURS.between(Instant.now(), reservation.getSoiree().getDateHeure());
        if (heuresAvant < 24) {
            throw new ValidationMetierException("Annulation impossible à moins de 24h de la soirée (RM-46)");
        }

        reservation.setStatut(StatutReservation.ANNULEE);
        reservationRepository.save(reservation);

        List<Ticket> tickets = ticketRepository.findByReservationId(reservationId);
        Instant now = Instant.now();
        tickets.forEach(t -> {
            t.setStatut(StatutTicket.ANNULE);
            t.setDateAnnulation(now);
            qrCodeTicketRepository.findByTicketId(t.getId())
                    .ifPresent(q -> { q.setValide(false); qrCodeTicketRepository.save(q); });
        });
        ticketRepository.saveAll(tickets);

        CategorieTicket categorie = trouverCategoriePourReservation(reservation);
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - reservation.getNbPlaces()));
        categorieTicketRepository.save(categorie);

        // Remboursement : décision client = traitement manuel, aucun appel gateway ici.
        notificationService.envoyerSms(null, reservation.getTelephoneReservant(), TypeNotification.BILLET_EMIS,
                "NKS : votre réservation a été annulée. Le remboursement (le cas échéant) sera traité "
                        + "manuellement par notre équipe.");
    }

    /**
     * GAP-03 : expose le vrai {@code qrUuid} de chaque billet d'une réservation (page publique
     * "Mes tickets", lien de secours "Voter sur place"). Même vérification que
     * {@link #annulerReservation} : réservation introuvable et mauvais téléphone renvoient
     * exactement la même exception, pour ne jamais laisser deviner si la réservation existe.
     */
    @Transactional(readOnly = true)
    public List<TicketAvecQrResponse> obtenirTicketsAvecQr(UUID reservationId, String telephone) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);

        if (reservation == null || !SmsGateway.normaliserTelephone(reservation.getTelephoneReservant())
                .equals(SmsGateway.normaliserTelephone(telephone))) {
            throw new AccesRefuseException("Réservation non associée à ce numéro de téléphone");
        }

        return ticketRepository.findByReservationId(reservationId).stream()
                .map(ticket -> {
                    QRCodeTicket qr = qrCodeTicketRepository.findByTicketId(ticket.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("QR code introuvable pour ce billet"));
                    return TicketAvecQrResponse.from(ticket, qr);
                })
                .toList();
    }

    private void liberer(Reservation reservation) {
        reservation.setStatut(StatutReservation.EXPIREE);
        reservationRepository.save(reservation);
        CategorieTicket categorie = trouverCategoriePourReservation(reservation);
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - reservation.getNbPlaces()));
        categorieTicketRepository.save(categorie);
    }

    private CategorieTicket trouverCategoriePourReservation(Reservation reservation) {
        // La catégorie n'est pas stockée sur Reservation (elle l'est sur chaque Ticket) ;
        // avant génération des tickets on la retrouve via la 1ère catégorie active de la soirée
        // correspondant au montant unitaire — limitation acceptable pour le MVP (1 catégorie
        // active par soirée dans le cas courant). À affiner si plusieurs catégories concurrentes.
        return categorieTicketRepository.findBySoireeId(reservation.getSoiree().getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de ticket introuvable pour la soirée"));
    }

    private void genererTickets(Reservation reservation, CategorieTicket categorie) {
        if (ticketRepository.existsByReservationId(reservation.getId())) {
            log.info("Billets déjà émis pour la réservation {} — ignoré", reservation.getId());
            return;
        }
        List<QRCodeTicket> qrCodesGeneres = new ArrayList<>();
        for (int i = 0; i < reservation.getNbPlaces(); i++) {
            Ticket ticket = Ticket.builder()
                    .reservation(reservation)
                    .soiree(reservation.getSoiree())
                    .categorie(categorie)
                    .nomSpectateur(reservation.getNomReservant())
                    .telephoneSpectateur(reservation.getTelephoneReservant())
                    .statut(StatutTicket.EMIS)
                    .build();
            ticket = ticketRepository.save(ticket);

            QRCodeTicket qr = QRCodeTicket.builder()
                    .ticket(ticket)
                    .codeUuid(UUID.randomUUID())
                    .valide(true)
                    .build();
            qrCodesGeneres.add(qrCodeTicketRepository.save(qr));
        }

        notificationService.envoyerSms(null, reservation.getTelephoneReservant(), TypeNotification.BILLET_EMIS,
                "NKS : votre paiement est confirmé ! Vos " + reservation.getNbPlaces()
                        + " billet(s) sont disponibles sur le site avec votre numéro de téléphone.");
        if (reservation.getEmailReservant() != null) {
            envoyerEmailBillets(reservation, qrCodesGeneres);
        }
    }

    /**
     * Correctif retour client (14/09/2026) : l'e-mail de billets utilisait du HTML brut sans
     * le template validé (logo NKS), ne joignait aucune image de billet et ne mentionnait pas
     * l'espace "Mes tickets". On reconstruit désormais le message avec le template partagé
     * {@link NotificationService#construireEmailHtml}, on joint une image PNG par billet
     * (QR + identifiants lisibles) et on ajoute un lien explicite vers l'espace. Envoi
     * synchrone best-effort (cf. Javadoc de {@link NotificationService#envoyerEmail(Utilisateur,
     * String, TypeNotification, String, String, List)}) : un échec ne doit jamais faire
     * échouer la réservation elle-même.
     */
    private void envoyerEmailBillets(Reservation reservation, List<QRCodeTicket> qrCodes) {
        List<EmailGateway.PieceJointe> piecesJointes = new ArrayList<>();
        for (int i = 0; i < qrCodes.size(); i++) {
            QRCodeTicket qr = qrCodes.get(i);
            try {
                byte[] image = genererImageBillet(reservation, qr, i + 1, qrCodes.size());
                piecesJointes.add(new EmailGateway.PieceJointe("billet-" + (i + 1) + ".png", image, "image/png"));
            } catch (Exception e) {
                log.error("Échec génération de l'image du billet (QR {}) pour la réservation {} : {}",
                        qr.getId(), reservation.getId(), e.getMessage());
            }
        }

        SoireeEvent soiree = reservation.getSoiree();
        String contenuHtml = "<p style=\"margin:0 0 16px;\">Votre réservation pour <strong style=\"color:#FFFFFF;\">"
                + soiree.getNom() + "</strong> est confirmée (" + reservation.getNbPlaces() + " place(s)).</p>"
                + "<p style=\"margin:0 0 16px;\">Tu trouveras ton/tes billet(s) (QR code) en pièce jointe de cet "
                + "e-mail. Tu peux aussi les retrouver à tout moment, ainsi que ton QR code, en te rendant sur ton "
                + "espace <strong style=\"color:#FFFFFF;\">« Mes tickets »</strong>.</p>";
        String corpsHtml = notificationService.construireEmailHtml(
                reservation.getNomReservant(), "Vos billets sont prêts !", contenuHtml,
                "Voir mes tickets", frontendBaseUrl + "/billetterie/mes-tickets");

        notificationService.envoyerEmail(null, reservation.getEmailReservant(), TypeNotification.BILLET_EMIS,
                "NKS — Vos billets", corpsHtml, piecesJointes);
    }

    /**
     * Encode exactement le même format que le scanner navigateur ({@code "NKS:" + qrUuid},
     * cf. {@code caisse.component.ts}) — cohérence indispensable, ce QR doit rester scannable
     * par {@code ScanService}. Génération côté serveur acceptable ici UNIQUEMENT parce que
     * l'image part directement et exclusivement à l'adresse fournie par l'acheteur (contexte
     * de confiance différent d'une API interrogeable par un tiers, cf. audit qrUuid).
     */
    private byte[] genererImageBillet(Reservation reservation, QRCodeTicket qr, int numeroBillet, int totalBillets)
            throws Exception {
        String contenuQr = "NKS:" + qr.getCodeUuid();
        int tailleQrSource = 260;
        BitMatrix matrix = new QRCodeWriter().encode(contenuQr, BarcodeFormat.QR_CODE, tailleQrSource, tailleQrSource);
        BufferedImage qrImageSource = MatrixToImageWriter.toBufferedImage(matrix);

        SoireeEvent soiree = reservation.getSoiree();
        BufferedImage logoNks = chargerLogoNks();
        BufferedImage logoTerrasse = chargerLogoTerrasse();

        BufferedImage image = new BufferedImage(LARGEUR_BILLET, HAUTEUR_BILLET, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // ── Fond général ──
            g.setColor(NKS_BG_PRIMARY);
            g.fillRect(0, 0, LARGEUR_BILLET, HAUTEUR_BILLET);

            // ── Zone visuelle haute : dégradé + logo NKS + nom/date/lieu de la soirée ──
            g.setPaint(new GradientPaint(0, 0, NKS_BG_SURFACE, 0, 420, NKS_BG_PRIMARY));
            g.fillRect(0, 0, LARGEUR_BILLET, 420);
            g.setPaint(null);

            int logoW = 150;
            int logoH = Math.round((float) logoNks.getHeight() / logoNks.getWidth() * logoW);
            g.drawImage(logoNks, (LARGEUR_BILLET - logoW) / 2, 48, logoW, logoH, null);

            g.setFont(new Font(Font.SERIF, Font.BOLD, 26));
            g.setColor(NKS_GOLD_LIGHT);
            int yTitre = drawMultilineCentered(g, soiree.getNom().toUpperCase(Locale.FRENCH),
                    LARGEUR_BILLET / 2, 48 + logoH + 46, LARGEUR_BILLET - 64, 32);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            g.setColor(NKS_TEXT_SECONDARY);
            if (soiree.getDateHeure() != null) {
                String dateFormatee = FORMAT_DATE_BILLET.format(soiree.getDateHeure().atZone(FUSEAU_OUAGA));
                drawCentered(g, dateFormatee, LARGEUR_BILLET / 2, yTitre + 14);
            }
            if (soiree.getLieu() != null && !soiree.getLieu().isBlank()) {
                drawCentered(g, soiree.getLieu(), LARGEUR_BILLET / 2, yTitre + 38);
            }

            // ── Bande diagonale : QR code + catégorie (structure "badge FESPACO") ──
            Polygon bande = new Polygon(
                    new int[]{0, LARGEUR_BILLET, LARGEUR_BILLET, 0},
                    new int[]{430, 470, 630, 590}, 4);
            g.setColor(NKS_BG_SURFACE);
            g.fillPolygon(bande);
            g.setColor(NKS_BANDE_CONTOUR);
            g.setStroke(new BasicStroke(1f));
            g.drawPolygon(bande);

            int qrTaille = 150;
            int qrX = 32;
            int qrY = 448;
            g.setColor(NKS_TEXT);
            g.fillRect(qrX - 8, qrY - 8, qrTaille + 16, qrTaille + 16);
            g.drawImage(qrImageSource, qrX, qrY, qrTaille, qrTaille, null);

            String categorieNom = Optional.ofNullable(qr.getTicket())
                    .map(Ticket::getCategorie)
                    .map(CategorieTicket::getNom)
                    .map(Enum::name)
                    .orElse("BILLET");
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.setColor(NKS_TEXT);
            g.drawString(categorieNom, qrX + qrTaille + 24, qrY + 60);

            if (totalBillets > 1) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.setColor(NKS_GOLD);
                g.drawString("Billet " + numeroBillet + "/" + totalBillets, qrX + qrTaille + 24, qrY + 88);
            }

            // ── Bandeau bas : branding + spectateur (structure "bandeau FESPACO") ──
            int yBandeau = 650;
            g.setPaint(new GradientPaint(0, yBandeau, NKS_GOLD, 0, HAUTEUR_BILLET, NKS_GOLD_LIGHT));
            g.fillRect(0, yBandeau, LARGEUR_BILLET, HAUTEUR_BILLET - yBandeau);
            g.setPaint(null);

            int logoBasH = 46;
            int logoBasW = Math.round((float) logoTerrasse.getWidth() / logoTerrasse.getHeight() * logoBasH);
            g.setColor(NKS_TEXT);
            g.fillRoundRect(28, yBandeau + 20, logoBasW + 12, logoBasH + 12, 8, 8);
            g.drawImage(logoTerrasse, 34, yBandeau + 26, logoBasW, logoBasH, null);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.setColor(NKS_BG_PRIMARY);
            drawRightAligned(g, reservation.getNomReservant(), LARGEUR_BILLET - 28, yBandeau + 48);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            drawRightAligned(g, "NIGHT KARAOKE STARS", LARGEUR_BILLET - 28, yBandeau + 72);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(NKS_FOOTER_TEXT);
            drawRightAligned(g, "Présente ce billet à l'entrée", LARGEUR_BILLET - 28, HAUTEUR_BILLET - 24);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
    }

    private static synchronized BufferedImage chargerLogoNks() throws IOException {
        if (logoNksCache == null) {
            try (InputStream in = BilletterieService.class.getResourceAsStream("/assets/billets/nks-logo.png")) {
                if (in == null) {
                    throw new IOException("Logo NKS introuvable dans les ressources classpath");
                }
                logoNksCache = ImageIO.read(in);
            }
        }
        return logoNksCache;
    }

    private static synchronized BufferedImage chargerLogoTerrasse() throws IOException {
        if (logoTerrasseCache == null) {
            try (InputStream in = BilletterieService.class.getResourceAsStream("/assets/billets/la-terrasse-logo.png")) {
                if (in == null) {
                    throw new IOException("Logo La Terrasse introuvable dans les ressources classpath");
                }
                logoTerrasseCache = ImageIO.read(in);
            }
        }
        return logoTerrasseCache;
    }

    private void drawCentered(Graphics2D g, String texte, int centreX, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(texte, centreX - metrics.stringWidth(texte) / 2, y);
    }

    private void drawRightAligned(Graphics2D g, String texte, int droiteX, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(texte, droiteX - metrics.stringWidth(texte), y);
    }

    /** Retour à la ligne manuel centré, miroir de texteMultiligne() côté client — renvoie le y final. */
    private int drawMultilineCentered(Graphics2D g, String texte, int centreX, int y, int largeurMax, int interligne) {
        FontMetrics metrics = g.getFontMetrics();
        String[] mots = texte.split(" ");
        StringBuilder ligne = new StringBuilder();
        int yy = y;
        for (String mot : mots) {
            String essai = ligne.length() == 0 ? mot : ligne + " " + mot;
            if (metrics.stringWidth(essai) > largeurMax && ligne.length() > 0) {
                drawCentered(g, ligne.toString(), centreX, yy);
                ligne = new StringBuilder(mot);
                yy += interligne;
            } else {
                ligne = new StringBuilder(essai);
            }
        }
        if (ligne.length() > 0) {
            drawCentered(g, ligne.toString(), centreX, yy);
        }
        return yy;
    }

    /**
     * Clôture de soirée (statut TERMINEE, cf. SoireeController.mettreAJour) : tout billet
     * encore EMIS (jamais scanné, jamais annulé) pour cette soirée expire — il ne peut plus
     * donner accès à un événement qui n'a plus lieu. Les billets déjà UTILISE/ANNULE ne sont
     * jamais touchés (idempotent : n'affecte que les tickets encore EMIS).
     */
    @Transactional
    public void expirerTicketsSoiree(UUID soireeId) {
        List<Ticket> ticketsEmis = ticketRepository.findBySoireeIdAndStatut(soireeId, StatutTicket.EMIS);
        if (ticketsEmis.isEmpty()) {
            return;
        }
        ticketsEmis.forEach(t -> t.setStatut(StatutTicket.EXPIRE));
        ticketRepository.saveAll(ticketsEmis);
        log.info("Soirée {} clôturée : {} billet(s) EMIS passé(s) à EXPIRE", soireeId, ticketsEmis.size());
    }

    @Transactional
    public Reservation genererTicketsGratuits(UUID soireeId, UUID categorieId, String nom, String telephone,
                                               int nbPlaces, Utilisateur admin) {
        CategorieTicket categorie = categorieTicketRepository.findByIdForUpdate(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable"));

        Reservation reservation = Reservation.builder()
                .soiree(categorie.getSoiree())
                .telephoneReservant(SmsGateway.normaliserTelephone(telephone))
                .nomReservant(nom)
                .nbPlaces(nbPlaces)
                .montantTotal(BigDecimal.ZERO)
                .statut(StatutReservation.CONFIRMEE)
                .gratuit(true)
                .adminEmission(admin)
                .build();
        categorie.setNbPlacesReservees(categorie.getNbPlacesReservees() + nbPlaces);
        categorieTicketRepository.save(categorie);
        reservation = reservationRepository.save(reservation);

        genererTickets(reservation, categorie);
        return reservation;
    }
}
