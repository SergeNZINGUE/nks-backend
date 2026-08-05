package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.NiveauPartenariat;
import bf.laterrasse.nks.domain.enums.Enums.StatutPartenaire;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "partenaires")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partenaire {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "site_web_url", length = 255)
    private String siteWebUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_partenariat", length = 20)
    private NiveauPartenariat niveauPartenariat;

    @Column(name = "contact_nom", length = 150)
    private String contactNom;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_telephone", length = 20)
    private String contactTelephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutPartenaire statut = StatutPartenaire.ACTIF;
}
