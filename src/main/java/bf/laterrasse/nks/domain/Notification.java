package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(name = "telephone_destinataire", length = 20)
    private String telephoneDestinataire;

    @Column(name = "email_destinataire", length = 255)
    private String emailDestinataire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CanalNotification canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_notification", nullable = false, length = 40)
    private TypeNotification typeNotification;

    @Column(length = 255)
    private String sujet;

    @Column(name = "corps_message", nullable = false, columnDefinition = "text")
    private String corpsMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_envoi", nullable = false, length = 15)
    @Builder.Default
    private StatutEnvoiNotification statutEnvoi = StatutEnvoiNotification.EN_ATTENTE;

    @Column(name = "nb_tentatives", nullable = false)
    @Builder.Default
    private Short nbTentatives = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Column(name = "date_creation", nullable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();

    @Column(name = "date_envoi")
    private Instant dateEnvoi;

    @Column(name = "reference_externe", columnDefinition = "text")
    private String referenceExterne;
}
