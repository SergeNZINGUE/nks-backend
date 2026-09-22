package bf.laterrasse.nks.dto.admin;

public record ReinitialiserMotDePasseCandidatResponse(
        String nouveauMotDePasse,
        String prenomNom,
        String email,
        String telephone
) {}